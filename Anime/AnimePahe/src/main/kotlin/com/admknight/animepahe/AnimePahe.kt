package com.admknight.animepahe

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.mvvm.safeAsync
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI

class AnimePahe : MainAPI() {
    companion object {
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")
        fun getType(t: String): TvType {
            return when {
                t.contains("OVA") || t.contains("Special") -> TvType.OVA
                t.contains("Movie") -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }
    }

    override var mainUrl = AnimePaheProviderPlugin.currentAnimepaheServer
    override var name = "AnimePahe"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.Anime, TvType.OVA)

    override val mainPage = listOf(MainPageData("Latest Releases", "$mainUrl/api?m=airing&page=", true))

    data class LatestData(
        @JsonProperty("anime_title") val animeTitle: String,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("snapshot") val snapshot: String?,
        @JsonProperty("anime_session") val animeSession: String
    )
    data class AnimePaheLatestReleases(@JsonProperty("data") val data: List<LatestData>)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(request.data + page, headers = headers).text
        val episodes = parseJson<AnimePaheLatestReleases>(response).data.map {
            newAnimeSearchResponse(it.animeTitle, LoadData(it.animeSession, unixTime, it.animeTitle).toJson()) {
                this.posterUrl = it.snapshot
                addDubStatus(DubStatus.Subbed, it.episode)
            }
        }
        return newHomePageResponse(HomePageList(request.name, episodes, true), true)
    }

    data class AnimePaheSearchData(
        @JsonProperty("title") val title: String,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("session") val session: String
    )
    data class AnimePaheSearch(@JsonProperty("data") val data: List<AnimePaheSearchData>)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api?m=search&l=8&q=$query"
        val req = app.get(url, headers = mapOf("referer" to "$mainUrl/", "Cookie" to "__ddg2_=1234567890")).text
        val data = parseJson<AnimePaheSearch>(req)

        return data.data.map {
            newAnimeSearchResponse(it.title, LoadData(it.session, unixTime, it.title).toJson()) {
                this.posterUrl = it.poster
                addDubStatus(DubStatus.Subbed, it.episodes)
            }
        }
    }

    data class AnimeData(
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("snapshot") val snapshot: String,
        @JsonProperty("session") val session: String,
        @JsonProperty("created_at") val createdAt: String
    )
    data class AnimePaheAnimeData(@JsonProperty("total") val total: Int, @JsonProperty("per_page") val perPage: Int, @JsonProperty("last_page") val lastPage: Int, @JsonProperty("data") val data: List<AnimeData>)

    data class LinkLoadData(
        @JsonProperty("mainUrl") val mainUrl: String,
        @JsonProperty("is_play_page") val is_play_page: Boolean,
        @JsonProperty("episode_num") val episode_num: Int,
        @JsonProperty("page") val page: Int,
        @JsonProperty("session") val session: String,
        @JsonProperty("episode_session") val episode_session: String,
    ) {
        suspend fun getUrl(): String? {
            return if (is_play_page) "$mainUrl/play/$session/$episode_session"
            else {
                val res = app.get("$mainUrl/api?m=release&id=$session&sort=episode_asc&page=${page + 1}", headers = headers).parsedSafe<AnimePaheAnimeData>()
                val epSession = res?.data?.firstOrNull { it.episode == episode_num }?.session ?: return null
                "$mainUrl/play/$session/$epSession"
            }
        }
    }

    private suspend fun generateListOfEpisodes(session: String, metaEpisodes: Map<String, MetaEpisode>?): List<Episode> {
        val episodes = ArrayList<Episode>()
        runCatching {
            val req = app.get("$mainUrl/api?m=release&id=$session&sort=episode_asc&page=1", headers = headers).text
            val data = parseJson<AnimePaheAnimeData>(req)
            val lastPage = data.lastPage
            
            (1..lastPage).forEach { page ->
                val pReq = if (page == 1) data else parseJson<AnimePaheAnimeData>(app.get("$mainUrl/api?m=release&id=$session&sort=episode_asc&page=$page", headers = headers).text)
                pReq.data.forEach { ep ->
                    val epNum = ep.episode.toString()
                    val meta = metaEpisodes?.get(epNum)
                    episodes.add(newEpisode(LinkLoadData(mainUrl, true, ep.episode, page, session, ep.session).toJson()) {
                        this.name = meta?.title?.get("en") ?: ep.title.ifEmpty { "Episode ${ep.episode}" }
                        this.episode = ep.episode
                        this.posterUrl = meta?.image ?: ep.snapshot
                        this.description = meta?.overview
                        this.score = Score.from10(meta?.rating)
                        addDate(ep.createdAt)
                    })
                }
            }
        }
        return episodes
    }

    data class LoadData(val session: String, val sessionDate: Long, val name: String)

    override suspend fun load(url: String): LoadResponse? {
        return safeAsync {
            val loadData = parseJson<LoadData>(url)
            val session = if (loadData.sessionDate + 600 < unixTime) {
                try { parseJson<LoadData>(search(loadData.name).first().url).session } catch (_: Exception) { loadData.session }
            } else loadData.session

            val html = app.get("$mainUrl/anime/$session", headers = headers).text
            val doc = Jsoup.parse(html)
            val title = doc.selectFirst("span.sr-only.unselectable")?.text() ?: doc.selectFirst("h2.japanese")?.text() ?: ""
            
            val recommendations = doc.select("div.anime-recommendation div.row").mapNotNull {
                val rTitle = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                val rSession = it.selectFirst("a")?.attr("href")?.substringAfter("/anime/", "")?.takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
                val json = LoadData(rSession, unixTime, rTitle).toJson()
                newMovieSearchResponse(rTitle, json, TvType.TvSeries) {
                    this.posterUrl = it.selectFirst("img")?.attr("data-src")?.ifEmpty { it.selectFirst("img")?.attr("src") }
                }
            }

            val year = Regex("Aired:</strong>[^,]*, (\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull()
            val status = if (doc.selectFirst("a[href='/anime/airing']") != null) ShowStatus.Ongoing else ShowStatus.Completed
            
            var anilistId: Int? = null
            var malId: Int? = null
            doc.select(".external-links > a").forEach { a ->
                val id = a.attr("href").substringAfterLast("/").toIntOrNull()
                if (a.attr("href").contains("anilist.co")) anilistId = id
                else if (a.attr("href").contains("myanimelist.net")) malId = id
            }

            val animeMetaData = runCatching { parseAnimeData(app.get("https://api.ani.zip/mappings?mal_id=$malId").text) }.getOrNull()
            val episodes = generateListOfEpisodes(session, animeMetaData?.episodes)

            newAnimeLoadResponse(title, url, getType(doc.selectFirst("a[href*='/anime/type/']")?.text() ?: "")) {
                this.posterUrl = doc.selectFirst(".anime-poster a")?.attr("href")
                this.backgroundPosterUrl = animeMetaData?.images?.find { it.coverType == "Fanart" }?.url
                this.year = year
                addEpisodes(DubStatus.Subbed, episodes)
                this.showStatus = status
                this.plot = doc.selectFirst(".anime-synopsis")?.text()
                this.tags = doc.select(".anime-genre > ul a").map { it.text() }.takeIf { it.isNotEmpty() }
                this.recommendations = recommendations
                addMalId(malId)
                addAniListId(anilistId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseJson<LinkLoadData>(data)
        val epUrl = parsed.getUrl() ?: return false
        val doc = app.get(epUrl, headers = headers).document
        
        doc.select("#resolutionMenu button").forEach {
            val dub = if ("eng" in it.select("span").text().lowercase()) "DUB" else "SUB"
            val text = it.text()
            val match = Regex("""(.+?)\s+·\s+(\d{3,4}p)""").find(text)
            val source = match?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"
            val qual = match?.groupValues?.getOrNull(2)?.substringBefore("p")?.toIntOrNull() ?: Qualities.Unknown.value
            val href = it.attr("data-src")
            
            if ("kwik" in href) {
                loadCustomExtractor("Animepahe $source [$dub]", href, mainUrl, subtitleCallback, callback, qual)
            }
        }

        doc.select("div#pickDownload > a").forEach {
            val dub = if ("eng" in it.select("span").text().lowercase()) "DUB" else "SUB"
            val match = Regex("""(.+?)\s+·\s+(\d{3,4}p)""").find(it.text())
            val source = match?.groupValues?.getOrNull(1) ?: "Unknown"
            val qual = match?.groupValues?.getOrNull(2)?.substringBefore("p")?.toIntOrNull()
            loadCustomExtractor("Animepahe Pahe $source [$dub]", it.attr("href"), mainUrl, subtitleCallback, callback, qual)
        }
        return true
    }
}
