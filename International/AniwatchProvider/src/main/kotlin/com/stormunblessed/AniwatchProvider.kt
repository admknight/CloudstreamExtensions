package com.admknight.aniwatch

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.delay
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.system.measureTimeMillis
import okhttp3.Call
import okhttp3.Callback
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class PollingData(
    @JsonProperty("sid") val sid: String? = null,
    @JsonProperty("upgrades") val upgrades: ArrayList<String> = arrayListOf(),
    @JsonProperty("pingInterval") val pingInterval: Int? = null,
    @JsonProperty("pingTimeout") val pingTimeout: Int? = null
)

private fun generateTimeStamp(): String {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
    var code = ""
    var time = unixTimeMS
    while (time > 0) {
        code += chars[(time % (chars.length)).toInt()]
        time /= chars.length
    }
    return code.reversed()
}

private suspend fun negotiateNewSid(baseUrl: String): PollingData? {
    for (i in 1..5) {
        val jsonText =
            app.get("$baseUrl&t=${generateTimeStamp()}").text.replaceBefore("{", "")
        tryParseJson<PollingData?>(jsonText)?.let { return it }
        delay(1000L * i)
    }
    return null
}

data class Tracks(
    @JsonProperty("file") val file: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("kind") val kind: String?
)

data class Sources(
    @JsonProperty("file") val file: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("label") val label: String?
)

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

private fun md5(input: ByteArray): ByteArray {
    return MessageDigest.getInstance("MD5").digest(input)
}

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

    Objects.requireNonNull(aesCBC).init(
        Cipher.DECRYPT_MODE, SecretKeySpec(
            decryptionKey.copyOfRange(0, 32),
            "AES"
        ),
        IvParameterSpec(decryptionKey.copyOfRange(32, decryptionKey.size))
    )
    val decryptedData = aesCBC!!.doFinal(encrypted)
    return String(decryptedData, StandardCharsets.UTF_8)
}

private inline fun <reified T> decryptMapped(input: String, key: String): T? {
    return tryParseJson(decrypt(input, key))
}

private fun decrypt(input: String, key: String): String {
    return decryptSourceUrl(
        generateKey(
            base64DecodeArray(input).copyOfRange(8, 16),
            key.toByteArray()
        ), input
    )
}

private suspend fun Sources.toExtractorLink(
    caller: MainAPI,
    name: String,
    extractorData: String? = null,
): List<ExtractorLink>? {
    return this.file?.let { file ->
        val isM3u8 = URI(this.file).path.endsWith(".m3u8") || this.type.equals(
            "hls",
            ignoreCase = true
        )
        return if (isM3u8) {
            val res = try {
                M3u8Helper().m3u8Generation(
                    M3u8Helper.M3u8Stream(
                        this.file,
                        null,
                        mapOf("Referer" to "https://mzzcloud.life/")
                    ), false
                )
                    .map { stream ->
                        newExtractorLink(
                            caller.name,
                            "${caller.name} $name",
                            stream.streamUrl,
                        ) {
                            this.referer = caller.mainUrl
                            this.quality = getQualityFromName(stream.quality?.toString())
                            this.type = ExtractorLinkType.M3U8
                            this.extractorData = extractorData
                        }
                    }
            } catch(e: Exception) { null }
            
            if (!res.isNullOrEmpty()) res else listOf(
                newExtractorLink(
                    caller.name,
                    "${caller.name} $name",
                    this.file,
                ) {
                    this.referer = caller.mainUrl
                    this.quality = getQualityFromName(this@toExtractorLink.label)
                    this.type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    this.extractorData = extractorData
                }
            )
        } else {
            listOf(
                newExtractorLink(
                    caller.name,
                    caller.name,
                    file,
                ) {
                    this.referer = caller.mainUrl
                    this.quality = getQualityFromName(this@toExtractorLink.label)
                    this.extractorData = extractorData
                }
            )
        }
    }
}

private fun Tracks.toSubtitleFile(): SubtitleFile? {
    return this.file?.let {
        SubtitleFile(
            this.label ?: "Unknown",
            it
        )
    }
}

suspend fun MainAPI.extractRabbitStream(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    useSidAuthentication: Boolean,
    /** Used for extractorLink name, input: Source name */
    extractorData: String? = null,
    decryptKey: String? = null,
    nameTransformer: (String) -> String,
) {
    val mainIframeUrl = url.substringBeforeLast("/")
    val mainIframeId = url.substringAfterLast("/").substringBefore("?")

    var sid: String? = null
    if (useSidAuthentication && extractorData != null) {
        negotiateNewSid(extractorData)?.also { pollingData ->
            app.post(
                "$extractorData&t=${generateTimeStamp()}&sid=${pollingData.sid}",
                requestBody = "40".toRequestBody(),
                timeout = 60
            )
            val text = app.get(
                "$extractorData&t=${generateTimeStamp()}&sid=${pollingData.sid}",
                timeout = 60
            ).text.replaceBefore("{", "")

            sid = tryParseJson<PollingData>(text)?.sid
            ioSafe { app.get("$extractorData&t=${generateTimeStamp()}&sid=${pollingData.sid}") }
        }
    }
    val getSourcesUrl = "${
        mainIframeUrl.replace(
            "/embed",
            "/ajax/embed"
        )
    }/getSources?id=$mainIframeId${sid?.let { "$&sId=$it" } ?: ""}"
    val response = app.get(
        getSourcesUrl,
        referer = mainUrl,
        headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Connection" to "keep-alive",
            "TE" to "trailers"
        )
    )

    val sourceObject = if (decryptKey != null) {
        val encryptedMap = response.parsedSafe<SourceObjectEncrypted>()
        val sources = encryptedMap?.sources
        if (sources == null || encryptedMap.encrypted == false) {
            response.parsedSafe()
        } else {
            val decrypted = decryptMapped<List<Sources>>(sources, decryptKey)
            SourceObject(
                sources = decrypted,
                tracks = encryptedMap.tracks
            )
        }
    } else {
        response.parsedSafe()
    } ?: return

    sourceObject.tracks?.forEach { track ->
        track?.toSubtitleFile()?.let { subtitleFile ->
            subtitleCallback.invoke(subtitleFile)
        }
    }

    val list = listOf(
        sourceObject.sources to "source 1",
        sourceObject.sources1 to "source 2",
        sourceObject.sources2 to "source 3",
        sourceObject.sourcesBackup to "source backup"
    )

    list.forEach { subList ->
        subList.first?.forEach { source ->
            source?.toExtractorLink(
                this,
                nameTransformer(subList.second),
                extractorData,
            )
                ?.forEach {
                    callback(it)
                }
        }
    }
}

suspend fun Call.await(): okhttp3.Response {
    return suspendCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: okhttp3.Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }
}

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

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    val epRegex = Regex("Ep (\\d+)/")
    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.select("a").attr("href"))
        val title = this.select("h3.film-name").text()
        val dubSub = this.select(".film-poster > .tick.ltr").text()

        val dubExist = dubSub.contains("dub", ignoreCase = true)
        val subExist = dubSub.contains("sub", ignoreCase = true)
        val episodes =
            this.selectFirst(".film-poster > .tick.rtl > .tick-eps")?.text()?.let { eps ->
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val html = app.get("$mainUrl/home").text
        val document = Jsoup.parse(html)

        val homePageList = ArrayList<HomePageList>()

        document.select("div.anif-block").forEach { block ->
            val header = block.select("div.anif-block-header").text().trim()
            val animes = block.select("li").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        document.select("section.block_area.block_area_home").forEach { block ->
            val header = block.select("h2.cat-heading").text().trim()
            val animes = block.select("div.flw-item").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return newHomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search?keyword=$query"
        val html = app.get(link).text
        val document = Jsoup.parse(html)

        return document.select(".flw-item").map {
            val title = it.selectFirst(".film-detail > .film-name > a")?.attr("title").toString()
            val filmPoster = it.selectFirst(".film-poster")
            val poster = filmPoster!!.selectFirst("img")?.attr("data-src")

            val episodes = filmPoster.selectFirst("div.rtl > div.tick-eps")?.text()?.let { eps ->
                val epRegex = Regex("Ep (\\d+)/")
                epRegex.find(eps)?.groupValues?.get(1)?.toIntOrNull()
            }
            val dubsub = filmPoster.selectFirst("div.ltr")?.text()
            val dubExist = dubsub?.contains("DUB") ?: false
            val subExist = dubsub?.contains("SUB") ?: false || dubsub?.contains("RAW") ?: false

            val tvType =
                getType(it.selectFirst(".film-detail > .fd-infor > .fdi-item")?.text().toString())
            val href = fixUrl(it.selectFirst(".film-name a")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist, subExist, episodes, episodes)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        val title = document.selectFirst(".anisc-detail > .film-name")?.text().toString()
        val poster = document.selectFirst(".anisc-poster img")?.attr("src")
        val tags = document.select(".anisc-info a[href*=\"/genre/\"]").map { it.text() }

        var year: Int? = null
        var japaneseTitle: String? = null
        var status: ShowStatus? = null

        for (info in document.select(".anisc-info > .item.item-title")) {
            val text = info?.text().toString()
            when {
                (year != null && japaneseTitle != null && status != null) -> break
                text.contains("Premiered") && year == null ->
                    year =
                        info.selectFirst(".name")?.text().toString().split(" ").last().toIntOrNull()

                text.contains("Japanese") && japaneseTitle == null ->
                    japaneseTitle = info.selectFirst(".name")?.text().toString()

                text.contains("Status") && status == null ->
                    status = getStatus(info.selectFirst(".name")?.text().toString())
            }
        }

        val description = document.selectFirst(".film-description.m-hide > .text")?.text()
        val animeId = URI(url).path.split("-").last()

        val episodes = Jsoup.parse(
            app.get("$mainUrl/ajax/v2/episode/list/$animeId").parsed<Response>().html
        ).select(".ss-list > a[href].ssl-item.ep-item").map {
            newEpisode(it.attr("href")) {
                this.name = it?.attr("title")
                this.episode = it.selectFirst(".ssli-order")?.text()?.toIntOrNull()
            }
        }

        val recommendations =
            document.select("#main-content > section > .tab-content > div > .film_list-wrap > .flw-item")
                .mapNotNull { head ->
                    val filmPoster = head?.selectFirst(".film-poster")
                    val epPoster = filmPoster?.selectFirst("img")?.attr("data-src")
                    val a = head?.selectFirst(".film-detail > .film-name > a")
                    val epHref = a?.attr("href")
                    val epTitle = a?.attr("title")
                    if (epHref == null || epTitle == null || epPoster == null) {
                        null
                    } else {
                        newAnimeSearchResponse(epTitle, fixUrl(epHref), TvType.Anime) {
                            this.posterUrl = epPoster
                        }
                    }
                }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            japName = japaneseTitle
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    private data class Response(
        @JsonProperty("result") val html: String
    )

    private data class RapidCloudResponse(
        @JsonProperty("link") val link: String
    )

    private suspend fun getKey(): String {
        return app.get("https://raw.githubusercontent.com/consumet/rapidclown/main/key.txt").text
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val servers: List<Pair<DubStatus, String>> = Jsoup.parse(
            app.get("$mainUrl/ajax/v2/episode/servers?episodeId=" + data.split("=")[1])
                .parsed<Response>().html
        ).select(".server-item[data-type][data-id]").map {
            Pair(
                if (it.attr("data-type") == "sub") DubStatus.Subbed else DubStatus.Dubbed,
                it.attr("data-id")
            )
        }

        servers.distinctBy { it.second }.forEach {
            val link = "$mainUrl/ajax/v2/episode/sources?id=${it.second}"
            val extractorLink = app.get(link).parsed<RapidCloudResponse>().link
            if (!loadExtractor(extractorLink, "https://rapid-cloud.ru/", subtitleCallback, callback)) {
                extractRabbitStream(
                    extractorLink,
                    subtitleCallback,
                    { videoLink -> if (!videoLink.url.contains("betterstream")) callback(videoLink) },
                    false,
                    decryptKey = getKey()
                ) { sourceName ->
                    sourceName + " - ${it.first}"
                }
            }
        }

        return true
    }
}
