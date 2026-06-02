package com.admknight.aniwatch

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests.Companion.await
import okhttp3.Interceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import android.util.Log
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.delay
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.system.measureTimeMillis
import com.lagradost.cloudstream3.utils.INFER_TYPE

private const val OPTIONS = "OPTIONS"

class AniwatchProvider : MainAPI() {
    override var mainUrl = "https://aniwatch.to"
    override var name = "Aniwatch"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    private val epRegex = Regex("Ep (\\d+)/")
    
    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.select("a").attr("href"))
        val title = this.select("h3.film-name").text()
        val dubSub = this.select(".film-poster > .tick.ltr").text()

        val dubExist = dubSub.contains("dub", ignoreCase = true)
        val subExist = dubSub.contains("sub", ignoreCase = true)
        val episodes = this.selectFirst(".film-poster > .tick.rtl > .tick-eps")?.text()?.let { eps ->
            epRegex.find(eps)?.groupValues?.get(1)?.toIntOrNull()
        }
        if (href.contains("/news/") || title.trim().equals("News", ignoreCase = true)) return null
        val posterUrl = fixUrl(this.select("img").attr("data-src"))
        val type = getType(this.select("div.fd-infor > span.fdi-item").text())

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist, subExist, episodes, episodes)
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/home").document
        val homePageList = ArrayList<HomePageList>()

        document.select("div.anif-block").forEach { block ->
            val header = block.select("div.anif-block-header").text().trim()
            val animes = block.select("li").mapNotNull { it.toSearchResult() }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        document.select("section.block_area.block_area_home").forEach { block ->
            val header = block.select("h2.cat-heading").text().trim()
            val animes = block.select("div.flw-item").mapNotNull { it.toSearchResult() }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return newHomePageResponse(homePageList)
    }

    private data class Response(
        @JsonProperty("status") val status: Boolean,
        @JsonProperty("html") val html: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search?keyword=$query"
        val document = app.get(link).document

        return document.select(".flw-item").map {
            val title = it.selectFirst(".film-detail > .film-name > a")?.attr("title").toString()
            val filmPoster = it.selectFirst(".film-poster")
            val poster = filmPoster?.selectFirst("img")?.attr("data-src")

            val episodes = filmPoster?.selectFirst("div.rtl > div.tick-eps")?.text()?.let { eps ->
                epRegex.find(eps)?.groupValues?.get(1)?.toIntOrNull()
            }
            val dubsub = filmPoster?.selectFirst("div.ltr")?.text()
            val dubExist = dubsub?.contains("DUB") ?: false
            val subExist = dubsub?.contains("SUB") ?: false || dubsub?.contains("RAW") ?: false

            val tvType = getType(it.selectFirst(".film-detail > .fd-infor > .fdi-item")?.text().toString())
            val href = fixUrl(it.selectFirst(".film-name a")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist, subExist, episodes, episodes)
            }
        }
    }

    private fun Element?.getActor(): Actor? {
        val image = fixUrlNull(this?.selectFirst(".pi-avatar > img")?.attr("data-src")) ?: return null
        val name = this?.selectFirst(".pi-detail > .pi-name")?.text() ?: return null
        return Actor(name = name, image = image)
    }

    data class SyncData(
        @JsonProperty("mal_id") val malId: String?,
        @JsonProperty("anilist_id") val aniListId: String?,
    )

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val syncData = tryParseJson<SyncData>(document.selectFirst("#syncData")?.data())
        val title = document.selectFirst(".anisc-detail > .film-name")?.text().toString()
        val poster = document.selectFirst(".anisc-poster img")?.attr("src")
        val tags = document.select(".anisc-info a[href*=\"/genre/\"]").map { it.text() }
        val subEpisodes = ArrayList<Episode>()
        val dubEpisodes = ArrayList<Episode>()
        
        var year: Int? = null
        var japaneseTitle: String? = null
        var status: ShowStatus? = null

        for (info in document.select(".anisc-info > .item.item-title")) {
            val text = info.text()
            when {
                text.contains("Premiered") -> year = info.selectFirst(".name")?.text()?.split(" ")?.last()?.toIntOrNull()
                text.contains("Japanese") -> japaneseTitle = info.selectFirst(".name")?.text()
                text.contains("Status") -> status = getStatus(info.selectFirst(".name")?.text().toString())
            }
        }

        val description = document.selectFirst(".film-description.m-hide > .text")?.text()
        val animeId = url.substringAfterLast("-")

        val epListHtml = app.get("$mainUrl/ajax/v2/episode/list/$animeId").parsed<Response>().html
        val epDocs = Jsoup.parse(epListHtml).select(".ss-list > a[href].ssl-item.ep-item")
        
        epDocs.forEach { uno ->
            val episodeID = uno.attr("href").split("=")[1]
            val serversHtml = app.get("$mainUrl/ajax/v2/episode/servers?episodeId=$episodeID").parsed<Response>().html
            val serverList = Jsoup.parse(serversHtml).select(".server-item[data-type][data-id]")
            
            serverList.forEach { it ->
                val serverId = it.attr("data-id")
                val dubStat = it.attr("data-type")
                val dataEps = "{\"server\":\"$serverId\",\"dubs\":\"$dubStat\"}"

                if (dubStat == "sub") {
                    subEpisodes.add(newEpisode(dataEps) {
                        this.name = uno.attr("title")
                        this.episode = uno.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                    })
                } else if (dubStat == "dub") {
                    dubEpisodes.add(newEpisode(dataEps) {
                        this.name = uno.attr("title")
                        this.episode = uno.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                    })
                }
            }
        }

        val actors = document.select("div.block-actors-content > div.bac-list-wrap > div.bac-item")
            .mapNotNull { head ->
                val subItems = head.select(".per-info")
                if (subItems.isEmpty()) return@mapNotNull null
                val firstItem = subItems.first()
                val role = when (firstItem?.selectFirst(".pi-detail > .pi-cast")?.text()?.trim()) {
                    "Supporting" -> ActorRole.Supporting
                    "Main" -> ActorRole.Main
                    else -> null
                }
                val mainActor = firstItem.getActor() ?: return@mapNotNull null
                val voiceActor = if (subItems.size >= 2) subItems[1].getActor() else null
                ActorData(actor = mainActor, role = role, voiceActor = voiceActor)
            }

        val recommendations = document.select("#main-content > section > .tab-content > div > .film_list-wrap > .flw-item")
                .mapNotNull { head ->
                    val filmPoster = head.selectFirst(".film-poster")
                    val epPoster = filmPoster?.selectFirst("img")?.attr("data-src")
                    val a = head.selectFirst(".film-detail > .film-name > a")
                    val epHref = a?.attr("href")
                    val epTitle = a?.attr("title")
                    if (epHref == null || epTitle == null || epPoster == null) null
                    else {
                        newAnimeSearchResponse(epTitle, fixUrl(epHref), TvType.Anime) {
                            this.posterUrl = epPoster
                        }
                    }
                }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.japName = japaneseTitle
            this.engName = title
            this.posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            addEpisodes(DubStatus.Subbed, subEpisodes)
            this.showStatus = status
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
            this.actors = actors
            addMalId(syncData?.malId?.toIntOrNull())
            addAniListId(syncData?.aniListId?.toIntOrNull())
        }
    }

    private data class RapidCloudResponse(
        @JsonProperty("link") val link: String
    )

    private var sidMap: HashMap<Int, String?> = hashMapOf()

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                if (request.url.toString().endsWith(".ts") && request.method != OPTIONS && !request.url.toString().contains("betterstream")) {
                    val newRequest = chain.request().newBuilder().apply {
                        sidMap[extractorLink.url.hashCode()]?.let { sid -> addHeader("SID", sid) }
                    }.build()
                    val options = request.newBuilder().method(OPTIONS, request.body).build()
                    ioSafe { app.baseClient.newCall(options).await() }
                    return chain.proceed(newRequest)
                }
                return chain.proceed(chain.request())
            }
        }
    }

    private suspend fun getKey(): String = app.get("https://raw.githubusercontent.com/enimax-anime/key/e6/key.txt").text

    data class Tracks(@JsonProperty("file") val file: String?, @JsonProperty("label") val label: String?, @JsonProperty("kind") val kind: String?)
    data class Sources(@JsonProperty("file") val file: String?, @JsonProperty("type") val type: String?, @JsonProperty("label") val label: String?)
    data class SourceObject(
        @JsonProperty("sources") val sources: List<Sources?>? = null,
        @JsonProperty("sources_1") val sources1: List<Sources?>? = null,
        @JsonProperty("sources_2") val sources2: List<Sources?>? = null,
        @JsonProperty("sourcesBackup") val sourcesBackup: List<Sources?>? = null,
        @JsonProperty("tracks") val tracks: List<Tracks?>? = null
    )
    data class SourceObjectEncrypted(
        @JsonProperty("sources") val sources: String?,
        @JsonProperty("encrypted") val encrypted: Boolean?,
        @JsonProperty("sources_1") val sources1: String?,
        @JsonProperty("sources_2") val sources2: String?,
        @JsonProperty("sourcesBackup") val sourcesBackup: String?,
        @JsonProperty("tracks") val tracks: List<Tracks?>?
    )
    data class IframeJson(@JsonProperty("link") val link: String? = null)

    data class SubDubInfo (@JsonProperty("server") val server: String, @JsonProperty("dubs") val dubs: String)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parseData = try { parseJson<SubDubInfo>(data) } catch (_: Exception) { return false }
        val linkUrl = "$mainUrl/ajax/v2/episode/sources?id=${parseData.server}"
        val extractorLink = app.get(linkUrl).parsed<RapidCloudResponse>().link
        
        if (!loadExtractor(extractorLink, "https://rapid-cloud.ru/", subtitleCallback, callback)) {
            val key = getKey()
            extractRabbitStream(extractorLink, subtitleCallback, { videoLink -> if (!videoLink.url.contains("betterstream")) callback(videoLink) }, false, decryptKey = key) { sourceName ->
                "$sourceName - ${parseData.dubs}"
            }
        }
        return true
    }

    companion object {
        data class PollingData(
            @JsonProperty("sid") val sid: String? = null,
            @JsonProperty("upgrades") val upgrades: ArrayList<String> = arrayListOf(),
            @JsonProperty("pingInterval") val pingInterval: Int? = null,
            @JsonProperty("pingTimeout") val pingTimeout: Int? = null
        )

        fun getType(t: String): TvType {
            return when {
                t.contains("OVA") || t.contains("Special") -> TvType.OVA
                t.contains("Movie") -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

        private fun generateTimeStamp(): String {
            val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
            var code = ""
            var time = unixTimeMS
            while (time > 0) {
                code += chars[(time % chars.length).toInt()]
                time /= chars.length
            }
            return code.reversed()
        }

        private suspend fun negotiateNewSid(baseUrl: String): PollingData? {
            for (i in 1..5) {
                val jsonText = app.get("$baseUrl&t=${generateTimeStamp()}").text.replaceBefore("{", "")
                parseJson<PollingData?>(jsonText)?.let { return it }
                delay(1000L * i)
            }
            return null
        }

        private suspend fun Sources.toExtractorLink(caller: MainAPI, name: String, extractorData: String? = null): List<ExtractorLink>? {
            return this.file?.let { file ->
                val isM3u8 = file.contains(".m3u8") || this.type.equals("hls", ignoreCase = true)
                return if (isM3u8) {
                    runCatching {
                        M3u8Helper().m3u8Generation(M3u8Helper.M3u8Stream(file, null, mapOf("Referer" to "https://mzzcloud.life/")), false)
                            .map { stream ->
                                newExtractorLink(caller.name, "${caller.name} $name", stream.streamUrl, INFER_TYPE) {
                                    this.referer = caller.mainUrl
                                    this.quality = getQualityFromName(stream.quality?.toString())
                                }
                            }
                    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(
                        newExtractorLink(caller.name, "${caller.name} $name", file, INFER_TYPE) {
                            this.referer = caller.mainUrl
                            this.quality = getQualityFromName(this@toExtractorLink.label)
                        }
                    )
                } else {
                    listOf(
                        newExtractorLink(caller.name, caller.name, file, INFER_TYPE) {
                            this.referer = caller.mainUrl
                            this.quality = getQualityFromName(this@toExtractorLink.label)
                        }
                    )
                }
            }
        }

        private fun Tracks.toSubtitleFile(): SubtitleFile? = this.file?.let { SubtitleFile(this.label ?: "Unknown", it) }
        private fun md5(input: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(input)

        private fun generateKey(salt: ByteArray, secret: ByteArray): ByteArray {
            var key = md5(secret + salt)
            var currentKey = key
            while (currentKey.size < 48) {
                key = md5(key + secret + salt)
                currentKey += key
            }
            return currentKey
        }

        private fun decryptSourceUrl(decryptionKey: ByteArray, sourceUrl: String): String {
            val cipherData = base64DecodeArray(sourceUrl)
            val encrypted = cipherData.copyOfRange(16, cipherData.size)
            val aesCBC = Cipher.getInstance("AES/CBC/PKCS5Padding")
            aesCBC.init(Cipher.DECRYPT_MODE, SecretKeySpec(decryptionKey.copyOfRange(0, 32), "AES"), IvParameterSpec(decryptionKey.copyOfRange(32, decryptionKey.size)))
            return String(aesCBC.doFinal(encrypted), StandardCharsets.UTF_8)
        }

        private inline fun <reified T> decryptMapped(input: String, key: String): T? = tryParseJson(decrypt(input, key))
        private fun decrypt(input: String, key: String): String = decryptSourceUrl(generateKey(base64DecodeArray(input).copyOfRange(8, 16), key.toByteArray()), input)

        suspend fun MainAPI.extractRabbitStream(
            url: String,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit,
            useSidAuthentication: Boolean,
            extractorData: String? = null,
            decryptKey: String? = null,
            nameTransformer: (String) -> String,
        ) {
            val mainIframeUrl = url.substringBeforeLast("/")
            val mainIframeId = url.substringAfterLast("/").substringBefore("?")
            var sid: String? = null
            
            if (useSidAuthentication && extractorData != null) {
                negotiateNewSid(extractorData)?.also { pollingData ->
                    app.post("$extractorData&t=${generateTimeStamp()}&sid=${pollingData.sid}", requestBody = "40".toRequestBody(), timeout = 60)
                    val text = app.get("$extractorData&t=${generateTimeStamp()}&sid=${pollingData.sid}", timeout = 60).text.replaceBefore("{", "")
                    sid = parseJson<PollingData>(text).sid
                    ioSafe { app.get("$extractorData&t=${generateTimeStamp()}&sid=${pollingData.sid}") }
                }
            }
            
            val getSourcesUrl = "${mainIframeUrl.replace("/embed", "/ajax/embed")}/getSources?id=$mainIframeId${sid?.let { "$&sId=$it" } ?: ""}"
            val response = app.get(getSourcesUrl, headers = mapOf("X-Requested-With" to "XMLHttpRequest"))

            val sourceObject = if (decryptKey != null) {
                val encryptedMap = response.parsedSafe<SourceObjectEncrypted>()
                val sources = encryptedMap?.sources
                if (sources == null || encryptedMap.encrypted == false) {
                    response.parsedSafe()
                } else {
                    SourceObject(sources = decryptMapped<List<Sources>>(sources, decryptKey), tracks = encryptedMap.tracks)
                }
            } else response.parsedSafe()

            sourceObject?.tracks?.forEach { track -> track?.toSubtitleFile()?.let { subtitleCallback.invoke(it) } }

            listOf(sourceObject?.sources to "source 1", sourceObject?.sources1 to "source 2", sourceObject?.sources2 to "source 3", sourceObject?.sourcesBackup to "source backup").forEach { subList ->
                subList.first?.forEach { source ->
                    source?.toExtractorLink(this, nameTransformer(subList.second), extractorData)?.forEach { callback(it) }
                }
            }
        }
    }
}
