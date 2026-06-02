package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import java.util.*

private fun String.toAscii() = this.map { it.code }.joinToString()

class KrunchyGeoBypasser {
    companion object {
        const val BYPASS_SERVER = "https://cr-unblocker.us.to/start_session"
        val headers = mapOf(
            "accept" to "*/*",
            "connection" to "keep-alive",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36".toAscii()
        )
        var sessionId: String? = null
        val session = CustomSession(app.baseClient)
    }

    data class KrunchySession(
        @JsonProperty("data") var data: DataInfo? = DataInfo(),
        @JsonProperty("error") var error: Boolean? = null,
        @JsonProperty("code") var code: String? = null
    )

    data class DataInfo(
        @JsonProperty("session_id") var sessionId: String? = null,
        @JsonProperty("country_code") var countryCode: String? = null,
    )

    private suspend fun getSessionId(): Boolean {
        return try {
            val response = app.get(BYPASS_SERVER, params = mapOf("version" to "1.1")).text
            val json = parseJson<KrunchySession>(response)
            sessionId = json.data?.sessionId
            true
        } catch (e: Exception) {
            sessionId = null
            false
        }
    }

    private suspend fun autoLoadSession(): Boolean {
        if (sessionId != null) return true
        getSessionId()
        delay(3000)
        return autoLoadSession()
    }

    suspend fun geoBypassRequest(url: String): NiceResponse {
        autoLoadSession()
        return session.get(url, headers = headers, cookies = mapOf("session_id" to sessionId!!))
    }
}

class CrunchyrollProvider : MainAPI() {
    companion object {
        val crUnblock = KrunchyGeoBypasser()
        val episodeNumRegex = Regex("""Episode (\d+)""")
    }

    override var mainUrl = "http://www.crunchyroll.com"
    override var name = "Crunchyroll"
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "$mainUrl/videos/anime/popular/ajax_page?pg=" to "Popular",
        "$mainUrl/videos/anime/simulcasts/ajax_page" to "Simulcasts"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryData = request.data
        val paginated = categoryData.endsWith("=")
        val pagedLink = if (paginated) categoryData + page else categoryData
        val items = mutableListOf<HomePageList>()

        if (page <= 1 && request.name == "Popular") {
            val doc = Jsoup.parse(crUnblock.geoBypassRequest(mainUrl).text)
            val featured = doc.select(".js-featured-show-list > li").mapNotNull { anime ->
                val url = fixUrlNull(anime?.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val imgEl = anime.selectFirst("img")
                val animeName = imgEl?.attr("alt") ?: ""
                val posterUrl = imgEl?.attr("src")?.replace("small", "full")
                newAnimeSearchResponse(animeName, url, TvType.Anime) {
                    this.posterUrl = posterUrl
                    addDubStatus(false, true)
                }
            }
            val recent = doc.select("div.welcome-countdown-day:contains(Now Showing) li").mapNotNull {
                val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val animeName = it.selectFirst("span.welcome-countdown-name")?.text() ?: ""
                val img = it.selectFirst("img")?.attr("src")?.replace("medium", "full")
                val isDub = animeName.contains("Dub)", true)
                val details = it.selectFirst("span.welcome-countdown-details")?.text()
                val epnum = if (details.isNullOrBlank()) null else episodeNumRegex.find(details)?.groupValues?.get(1) ?: "0"
                
                newAnimeSearchResponse("★ $animeName ★", link.replace(Regex("(\\/episode.*)"), ""), TvType.Anime) {
                    this.posterUrl = fixUrlNull(img)
                    addDubStatus(isDub, !isDub)
                }
            }
            if (recent.isNotEmpty()) {
                items.add(HomePageList("Now Showing", recent))
            }
            if (featured.isNotEmpty()) {
                items.add(HomePageList("Featured", featured))
            }
        }

        if (paginated || (!paginated && page <= 1)) {
            crUnblock.geoBypassRequest(pagedLink).let { respText ->
                val soup = Jsoup.parse(respText.text)
                val episodes = soup.select("li").mapNotNull {
                    val innerA = it.selectFirst("a") ?: return@mapNotNull null
                    val urlEps = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
                    newAnimeSearchResponse(innerA.attr("title"), urlEps, TvType.Anime) {
                        this.posterUrl = it.selectFirst("img")?.attr("src")
                        addDubStatus(false, true)
                    }
                }
                if (episodes.isNotEmpty()) {
                    items.add(HomePageList(request.name, episodes))
                }
            }
        }

        if (items.isNotEmpty()) {
            return newHomePageResponse(items)
        }
        throw ErrorLoadingException()
    }

    private fun getCloseMatches(sequence: String, items: Collection<String>): List<String> {
        val a = sequence.trim().lowercase()
        return items.mapNotNull { item ->
            val b = item.trim().lowercase()
            if (b.contains(a) || a.contains(b)) item else null
        }
    }

    private data class CrunchyAnimeData(
        @JsonProperty("name") val name: String,
        @JsonProperty("img") var img: String,
        @JsonProperty("link") var link: String
    )

    private data class CrunchyJson(
        @JsonProperty("data") val data: List<CrunchyAnimeData>,
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val json = crUnblock.geoBypassRequest("http://www.crunchyroll.com/ajax/?req=RpcApiSearch_GetSearchCandidates").text.split("*/")[0].replace("\\/", "/")
        val data = parseJson<CrunchyJson>(
            json.split("\n").mapNotNull { if (!it.startsWith("/")) it else null }.joinToString("\n")
        ).data

        val results = getCloseMatches(query, data.map { it.name })
        if (results.isEmpty()) return emptyList()
        val searchResults = ArrayList<SearchResponse>()

        for (anime in data) {
            if (results.contains(anime.name)) {
                val isDub = anime.name.contains("Dub)", true)
                searchResults.add(
                    newAnimeSearchResponse(anime.name, fixUrl(anime.link), TvType.Anime) {
                        this.posterUrl = anime.img.replace("small", "full")
                        addDubStatus(isDub, !isDub)
                    }
                )
            }
        }
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = Jsoup.parse(crUnblock.geoBypassRequest(url).text)
        val title = soup.selectFirst("#showview-content-header .ellipsis")?.text()?.trim() ?: ""
        val posterU = soup.selectFirst(".poster")?.attr("src")

        val p = soup.selectFirst(".description")
        var description = p?.selectFirst(".more")?.text()?.trim() ?: p?.selectFirst("span")?.text()?.trim()

        val genres = soup.select(".large-margin-bottom > ul:nth-child(2) li:nth-child(2) a")
            .map { it.text().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
        val year = genres.mapNotNull { it.toIntOrNull() }.minOrNull()

        val episodes = mutableListOf<Episode>()
        soup.select(".season").forEach { season ->
            val seasonName = season.selectFirst("a.season-dropdown")?.text()?.trim()
            season.select(".episode").forEach { ep ->
                val epTitle = ep.selectFirst(".short-desc")?.text()
                val epNumMatch = episodeNumRegex.find(ep.selectFirst("span.ellipsis")?.text().toString())
                val epNum = epNumMatch?.groupValues?.get(1)
                
                var epPoster = ep.selectFirst("img.landscape")?.attr("data-thumbnailurl") ?: ep.selectFirst("img")?.attr("src")
                val epDesc = (if (epNum == null) "" else "Episode $epNum") + (if (!seasonName.isNullOrEmpty()) " - $seasonName" else "")
                val isPremium = epPoster?.contains("widestar", ignoreCase = true) ?: false
                
                episodes.add(newEpisode(fixUrl(ep.attr("href"))) {
                    this.name = if (isPremium) "★ $epTitle ★" else epTitle
                    this.description = epDesc
                    this.posterUrl = epPoster?.replace("widestar", "full")?.replace("wide", "full")
                    this.episode = epNum?.toIntOrNull()
                })
            }
        }
        
        val recommendations = soup.select(".other-series > ul li").mapNotNull { element ->
            val recTitle = element.select("span.ellipsis[dir=auto]").text() ?: return@mapNotNull null
            val image = element.select("img")?.attr("src")
            val recUrl = fixUrl(element.select("a").attr("href"))
            newAnimeSearchResponse(recTitle, recUrl, TvType.Anime) {
                this.posterUrl = image
                addDubStatus(recTitle.contains("(DUB)") || recTitle.contains("Dub"), true)
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = posterU
            this.engName = title
            addEpisodes(DubStatus.Subbed, episodes)
            this.plot = description
            this.tags = genres
            this.year = year
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val contentRegex = Regex("""vilos\.config\.media = (\{.+\})""")
        val response = crUnblock.geoBypassRequest(data)
        val dat = contentRegex.find(response.text)?.groupValues?.get(1)

        if (!dat.isNullOrEmpty()) {
            val json = parseJson<KrunchyVideo>(dat)
            json.streams.forEach { stream ->
                if (stream.format == "adaptive_hls" || stream.format == "adaptive_dash" || stream.format == "trailer_hls") {
                    callback(
                        newExtractorLink(
                            "Crunchyroll",
                            "Crunchy - ${stream.title()}",
                            stream.url,
                            INFER_TYPE
                        ) {
                            this.quality = getQualityFromName(stream.resolution)
                        }
                    )
                }
            }
            json.subtitles.forEach {
                val langclean = it.language.replace("esLA", "Spanish")
                    .replace("enUS", "English")
                    .replace("esES", "Spanish (Spain)")
                subtitleCallback(SubtitleFile(langclean, it.url))
            }
            return true
        }
        return false
    }

    data class Subtitles(
        @JsonProperty("language") val language: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("title") val title: String?,
        @JsonProperty("format") val format: String?
    )

    data class Streams(
        @JsonProperty("format") val format: String?,
        @JsonProperty("audio_lang") val audioLang: String?,
        @JsonProperty("hardsub_lang") val hardsubLang: String?,
        @JsonProperty("url") val url: String,
        @JsonProperty("resolution") val resolution: String?,
        @JsonProperty("title") var title: String?
    ) {
        fun title(): String {
            return when {
                this.hardsubLang == "enUS" && this.audioLang == "jaJP" -> "Hardsub (English)"
                this.hardsubLang == "esLA" && this.audioLang == "jaJP" -> "Hardsub (Latino)"
                this.hardsubLang == "esES" && this.audioLang == "jaJP" -> "Hardsub (Español España)"
                this.audioLang == "esLA" -> "Latino"
                this.audioLang == "esES" -> "Español España"
                this.audioLang == "enUS" -> "English (US)"
                else -> "RAW"
            }
        }
    }

    data class KrunchyVideo(
        @JsonProperty("streams") val streams: List<Streams>,
        @JsonProperty("subtitles") val subtitles: List<Subtitles>,
    )
}
