package com.admknight.sflix

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
                ExtractorLink(
                    caller.name,
                    "${caller.name} $name",
                    this.file,
                    caller.mainUrl,
                    getQualityFromName(this.label),
                    isM3u8,
                    extractorData = extractorData
                )
            )
        } else {
            listOf(
                ExtractorLink(
                    caller.name,
                    caller.name,
                    file,
                    caller.mainUrl,
                    getQualityFromName(this.label),
                    false,
                    extractorData = extractorData
                )
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

suspend fun getKey(): String? {
    return app.get("https://raw.githubusercontent.com/consumet/rapidclown/rabbitstream/key.txt")
        .text
}

fun String?.isValidServer(): Boolean {
    val list = listOf("upcloud", "vidcloud", "streamlare")
    return list.contains(this?.lowercase(Locale.ROOT))
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
        val html = app.get("$mainUrl/home").text
        val document = Jsoup.parse(html)

        val all = ArrayList<HomePageList>()

        val map = mapOf(
            "Trending Movies" to "div#trending-movies",
            "Trending TV Shows" to "div#trending-tv",
        )
        map.forEach {
            all.add(HomePageList(
                it.key,
                document.select(it.value).select("div.flw-item").map { element ->
                    element.toSearchResult()
                }
            ))
        }

        document.select("section.block_area.block_area_home.section-id-02").forEach {
            val title = it.select("h2.cat-heading").text().trim()
            val elements = it.select("div.flw-item").map { element ->
                element.toSearchResult()
            }
            all.add(HomePageList(title, elements))
        }

        return newHomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "-")}"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("div.flw-item").map {
            val title = it.select("h2.film-name").text()
            val href = fixUrl(it.select("a").attr("href"))
            val year = it.select("span.fdi-item").text().toIntOrNull()
            val image = it.select("img").attr("data-src")
            val isMovie = href.contains("/movie/")

            val metaInfo = it.select("div.fd-infor > span.fdi-item")
            val quality = getQualityFromString(metaInfo.getOrNull(1)?.text())

            if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = image
                    this.year = year
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = image
                    this.year = year
                    this.quality = quality
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val details = document.select("div.detail_page-watch")
        val img = details.select("img.film-poster-img")
        val posterUrl = img.attr("src")
        val title = img.attr("title") ?: throw ErrorLoadingException("No Title")

        var duration = document.selectFirst(".fs-item > .duration")?.text()?.trim()
        var year: Int? = null
        var tags: List<String>? = null
        var cast: List<String>? = null
        val youtubeTrailer = document.selectFirst("iframe#iframe-trailer")?.attr("data-src")
        val rating = document.selectFirst(".fs-item > .imdb")?.text()?.trim()
            ?.removePrefix("IMDB:")

        document.select("div.elements > .row > div > .row-line").forEach { element ->
            val type = element?.select(".type")?.text() ?: return@forEach
            when {
                type.contains("Released") -> {
                    year = Regex("\\d+").find(
                        element.ownText() ?: return@forEach
                    )?.groupValues?.firstOrNull()?.toIntOrNull()
                }
                type.contains("Genre") -> {
                    tags = element.select("a").mapNotNull { it.text() }
                }
                type.contains("Cast") -> {
                    cast = element.select("a").mapNotNull { it.text() }
                }
                type.contains("Duration") -> {
                    duration = duration ?: element.ownText().trim()
                }
            }
        }
        val plot = details.select("div.description").text().replace("Overview:", "").trim()

        val isMovie = url.contains("/movie/")

        val idRegex = Regex(""".*-(\d+)""")
        val dataId = details.attr("data-id")
        val id = if (dataId.isNullOrEmpty())
            idRegex.find(url)?.groupValues?.get(1)
                ?: throw ErrorLoadingException("Unable to get id from '$url'")
        else dataId

        val recommendations =
            document.select("div.film_list-wrap > div.flw-item").mapNotNull { element ->
                val titleHeader =
                    element.select("div.film-detail > .film-name > a") ?: return@mapNotNull null
                val recUrl = fixUrlNull(titleHeader.attr("href")) ?: return@mapNotNull null
                val recTitle = titleHeader.text() ?: return@mapNotNull null
                val poster = element.select("div.film-poster > img").attr("data-src")
                newMovieSearchResponse(recTitle, recUrl, if (recUrl.contains("/movie/")) TvType.Movie else TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }

        if (isMovie) {
            val episodesUrl = "$mainUrl/ajax/movie/episodes/$id"
            val episodes = app.get(episodesUrl).text

            val sourceIds = Jsoup.parse(episodes).select("a").mapNotNull { element ->
                var sourceId = element.attr("data-id")
                if (sourceId.isNullOrEmpty())
                    sourceId = element.attr("data-linkid")

                if (element.select("span").text().trim().isValidServer()) {
                    if (sourceId.isNullOrEmpty()) {
                        fixUrlNull(element.attr("href"))
                    } else {
                        "$url.$sourceId".replace("/movie/", "/watch-movie/")
                    }
                } else {
                    null
                }
            }

            val comingSoon = sourceIds.isEmpty()

            return newMovieLoadResponse(title, url, TvType.Movie, sourceIds) {
                this.year = year
                this.posterUrl = posterUrl
                this.plot = plot
                addDuration(duration)
                addActors(cast)
                this.tags = tags
                this.recommendations = recommendations
                this.comingSoon = comingSoon
                addTrailer(youtubeTrailer)
                this.score = Score.from10(rating)
            }
        } else {
            val seasonsDocument = app.get("$mainUrl/ajax/v2/tv/seasons/$id").document
            val episodes = arrayListOf<Episode>()
            var seasonItems = seasonsDocument.select("div.dropdown-menu.dropdown-menu-model > a")
            if (seasonItems.isNullOrEmpty())
                seasonItems = seasonsDocument.select("div.dropdown-menu > a.dropdown-item")
            seasonItems.mapIndexed { season, element ->
                val seasonId = element.attr("data-id")
                if (seasonId.isNullOrBlank()) return@mapIndexed

                var episode = 0
                val seasonEpisodes = app.get("$mainUrl/ajax/v2/season/episodes/$seasonId").document
                var seasonEpisodesItems =
                    seasonEpisodes.select("div.flw-item.film_single-item.episode-item.eps-item")
                if (seasonEpisodesItems.isNullOrEmpty()) {
                    seasonEpisodesItems =
                        seasonEpisodes.select("ul > li > a")
                }
                seasonEpisodesItems.forEach {
                    val episodeImg = it?.select("img")
                    val episodeTitle = episodeImg?.attr("title") ?: it.ownText()
                    val episodePosterUrl = episodeImg?.attr("src")
                    val episodeData = it.attr("data-id") ?: return@forEach

                    episode++

                    val episodeNum =
                        (it.select("div.episode-number").text()
                            ?: episodeTitle).let { str ->
                            Regex("""\d+""").find(str)?.groupValues?.firstOrNull()
                                ?.toIntOrNull()
                        } ?: episode

                    episodes.add(
                        newEpisode(Pair(url, episodeData)) {
                            this.posterUrl = fixUrlNull(episodePosterUrl)
                            this.name = episodeTitle?.removePrefix("Episode $episodeNum: ")
                            this.season = season + 1
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
                this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = (tryParseJson<Pair<String, String>>(data)?.let { (prefix, server) ->
            val episodesUrl = "$mainUrl/ajax/v2/episode/servers/$server"

            app.get(episodesUrl).document.select("a").mapNotNull { element ->
                val id = element?.attr("data-id") ?: return@mapNotNull null
                if (element.select("span").text().trim().isValidServer()) {
                    "$prefix.$id".replace("/tv/", "/watch-tv/")
                } else {
                    null
                }
            }
        } ?: tryParseJson<List<String>>(data))?.distinct()

        urls?.forEach { url ->
            val serverId = url.substringAfterLast(".")
            val iframeLink = try {
                app.get("${this.mainUrl}/ajax/get_link/$serverId").parsed<IframeJson>().link
            } catch(e: Exception) { null } ?: return@forEach

            if (!loadExtractor(iframeLink, null, subtitleCallback, callback)) {
                extractRabbitStream(
                    iframeLink,
                    subtitleCallback,
                    callback,
                    false,
                    decryptKey = getKey()
                ) { it }
            }
        }

        return !urls.isNullOrEmpty()
    }

    private fun Element.toSearchResult(): SearchResponse {
        val inner = this.selectFirst("div.film-poster")
        val img = inner!!.select("img")
        val title = img.attr("title")
        val posterUrl = img.attr("data-src") ?: img.attr("src")
        val href = fixUrl(inner.select("a").attr("href"))
        val isMovie = href.contains("/movie/")
        val otherInfo =
            this.selectFirst("div.film-detail > div.fd-infor")?.select("span")?.toList() ?: listOf()
        var year: Int? = null
        var quality: SearchQuality? = null
        when (otherInfo.size) {
            1 -> {
                year = otherInfo[0]?.text()?.trim()?.toIntOrNull()
            }
            2 -> {
                year = otherInfo[0]?.text()?.trim()?.toIntOrNull()
            }
            3 -> {
                quality = getQualityFromString(otherInfo[1]?.text())
                year = otherInfo[2]?.text()?.trim()?.toIntOrNull()
            }
        }

        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
                this.quality = quality
            }
        }
    }

    data class IframeJson(
        @JsonProperty("link") val link: String? = null,
    )
}
