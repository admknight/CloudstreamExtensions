package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
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
import com.lagradost.api.Log

open class SflixProvider : MainAPI() {
    override var mainUrl = "https://sflix.to"
    override var name = "Sflix.to"

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.None

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/home").document
        val all = ArrayList<HomePageList>()

        val map = mapOf(
            "Trending Movies" to "div#trending-movies",
            "Trending TV Shows" to "div#trending-tv",
        )
        map.forEach { (name, selector) ->
            all.add(HomePageList(
                name,
                document.select(selector).select("div.flw-item").map { it.toSearchResult() }
            ))
        }

        document.select("section.block_area.block_area_home.section-id-02").forEach {
            val title = it.select("h2.cat-heading").text().trim()
            val elements = it.select("div.flw-item").map { it.toSearchResult() }
            all.add(HomePageList(title, elements))
        }

        return newHomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "-")}"
        val document = app.get(url).document

        return document.select("div.flw-item").map {
            val title = it.select("h2.film-name").text()
            val href = fixUrl(it.select("a").attr("href"))
            val image = it.select("img").attr("data-src")
            val isMovie = href.contains("/movie/")

            val metaInfo = it.select("div.fd-infor > span.fdi-item")
            val quality = getQualityFromString(metaInfo.getOrNull(1)?.text())

            newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = image
                this.quality = quality
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val details = app.get(url).document.selectFirst("div.detail_page-watch") ?: throw ErrorLoadingException("No detail found")
        val img = details.selectFirst("img.film-poster-img")
        val posterUrl = img?.attr("src")
        val title = img?.attr("title") ?: throw ErrorLoadingException("No Title")

        var duration: String? = details.selectFirst(".fs-item > .duration")?.text()?.trim()
        var year: Int? = null
        var tags: List<String>? = null
        var cast: List<String>? = null
        val youtubeTrailer = details.selectFirst("iframe#iframe-trailer")?.attr("data-src")
        val scoreValue = Score.from10(details.selectFirst(".fs-item > .imdb")?.text()?.substringAfter("IMDb:")?.trim())

        details.select("div.elements > .row > div > .row-line").forEach { element ->
            val type = element.select(".type").text()
            when {
                type.contains("Released") -> {
                    year = Regex("\\d+").find(element.ownText())?.value?.toIntOrNull()
                }
                type.contains("Genre") -> {
                    tags = element.select("a").map { it.text() }
                }
                type.contains("Cast") -> {
                    cast = element.select("a").map { it.text() }
                }
                type.contains("Duration") -> {
                    duration = duration ?: element.ownText().trim()
                }
            }
        }
        val plot = details.selectFirst("div.description")?.text()?.replace("Overview:", "")?.trim()

        val isMovie = url.contains("/movie/")
        val dataId = details.attr("data-id")
        val id = if (dataId.isNullOrEmpty())
            Regex(""".*-(\d+)""").find(url)?.groupValues?.get(1) ?: throw ErrorLoadingException("Unable to get id")
        else dataId

        val recommendations = details.select("div.film_list-wrap > div.flw-item").mapNotNull { element ->
            val titleHeader = element.selectFirst("div.film-detail > .film-name > a") ?: return@mapNotNull null
            val recUrl = fixUrlNull(titleHeader.attr("href")) ?: return@mapNotNull null
            val recTitle = titleHeader.text()
            val poster = element.selectFirst("div.film-poster > img")?.attr("data-src")
            newMovieSearchResponse(recTitle, recUrl, if (recUrl.contains("/movie/")) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = poster
            }
        }

        if (isMovie) {
            val episodesHtml = app.get("$mainUrl/ajax/movie/episodes/$id").text
            val sourceIds = Jsoup.parse(episodesHtml).select("a").mapNotNull { element ->
                val sourceId = element.attr("data-id").ifEmpty { element.attr("data-linkid") }
                if (element.selectFirst("span")?.text()?.trim()?.isValidServer() == true) {
                    if (sourceId.isNullOrEmpty()) fixUrlNull(element.attr("href"))
                    else "$url.$sourceId".replace("/movie/", "/watch-movie/")
                } else null
            }

            return newMovieLoadResponse(title, url, TvType.Movie, sourceIds) {
                this.year = year
                this.posterUrl = posterUrl
                this.plot = plot
                addDuration(duration)
                addActors(cast)
                this.tags = tags
                this.recommendations = recommendations
                this.comingSoon = sourceIds.isEmpty()
                addTrailer(youtubeTrailer)
                this.score = scoreValue
            }
        } else {
            val seasonsDocument = app.get("$mainUrl/ajax/v2/tv/seasons/$id").document
            val episodes = arrayListOf<Episode>()
            val seasonItems = seasonsDocument.select("div.dropdown-menu > a")
            
            seasonItems.forEachIndexed { seasonIdx, element ->
                val seasonId = element.attr("data-id")
                if (seasonId.isBlank()) return@forEachIndexed

                val seasonEpisodes = app.get("$mainUrl/ajax/v2/season/episodes/$seasonId").document
                val seasonEpisodesItems = seasonEpisodes.select("div.flw-item.eps-item, ul > li > a")
                
                seasonEpisodesItems.forEachIndexed { epIdx, it ->
                    val episodeImg = it.selectFirst("img")
                    val episodeTitle = episodeImg?.attr("title") ?: it.ownText()
                    val episodePosterUrl = episodeImg?.attr("src")
                    val episodeData = it.attr("data-id") ?: return@forEachIndexed

                    val episodeNum = Regex("""\d+""").find(it.selectFirst("div.episode-number")?.text() ?: episodeTitle)?.value?.toIntOrNull() ?: (epIdx + 1)

                    episodes.add(
                        newEpisode(Pair(url, episodeData)) {
                            this.posterUrl = fixUrlNull(episodePosterUrl)
                            this.name = episodeTitle.removePrefix("Episode $episodeNum: ").trim()
                            this.season = seasonIdx + 1
                            this.episode = episodeNum
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                addDuration(duration)
                addActors(cast)
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(youtubeTrailer)
                this.score = scoreValue
            }
        }
    }

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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = (tryParseJson<Pair<String, String>>(data)?.let { (prefix, server) ->
            app.get("$mainUrl/ajax/v2/episode/servers/$server").document.select("a").mapNotNull { element ->
                val id = element.attr("data-id") ?: return@mapNotNull null
                if (element.selectFirst("span")?.text()?.trim()?.isValidServer() == true) {
                    "$prefix.$id".replace("/tv/", "/watch-tv/")
                } else null
            }
        } ?: tryParseJson<List<String>>(data))?.distinct()

        urls?.forEach { url ->
            runCatching {
                val serverId = url.substringAfterLast(".")
                val iframeLink = app.get("${this.mainUrl}/ajax/get_link/$serverId").parsed<IframeJson>().link ?: return@forEach

                if (!loadExtractor(iframeLink, null, subtitleCallback, callback)) {
                    val key = getKey()
                    extractRabbitStream(iframeLink, subtitleCallback, callback, false, decryptKey = key) { it }
                }
            }
        }
        return !urls.isNullOrEmpty()
    }

    private fun Element.toSearchResult(): SearchResponse {
        val inner = this.selectFirst("div.film-poster") ?: return newMovieSearchResponse("", "", TvType.Movie) {}
        val img = inner.selectFirst("img")
        val title = img?.attr("title") ?: ""
        val posterUrl = img?.attr("data-src") ?: img?.attr("src")
        val href = fixUrl(inner.selectFirst("a")?.attr("href") ?: "")
        val isMovie = href.contains("/movie/")
        val otherInfo = this.select("div.film-detail > div.fd-infor > span").map { it.text().trim() }
        
        var year: Int? = null
        var quality: SearchQuality? = null
        when (otherInfo.size) {
            1 -> year = otherInfo[0].toIntOrNull()
            2 -> year = otherInfo[0].toIntOrNull()
            3 -> {
                quality = getQualityFromString(otherInfo[1])
                year = otherInfo[2].toIntOrNull()
            }
        }

        return newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.year = year
            this.quality = quality
        }
    }

    companion object {
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
                code += chars[(time % chars.length).toInt()]
                time /= chars.length
            }
            return code.reversed()
        }

        suspend fun getKey(): String? = app.get("https://raw.githubusercontent.com/consumet/rapidclown/rabbitstream/key.txt").text

        private suspend fun negotiateNewSid(baseUrl: String): PollingData? {
            for (i in 1..5) {
                val jsonText = app.get("$baseUrl&t=${generateTimeStamp()}").text.replaceBefore("{", "")
                parseJson<PollingData?>(jsonText)?.let { return it }
                delay(1000L * i)
            }
            return null
        }

        fun String?.isValidServer(): Boolean {
            val list = listOf("upcloud", "vidcloud", "streamlare")
            return list.contains(this?.lowercase(Locale.ROOT))
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
