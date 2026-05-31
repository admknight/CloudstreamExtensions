package com.admknight.ultima

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.syncproviders.SyncRepo
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi.Companion.MediaObject
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi.Companion.getPosterUrl
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.admknight.ultima.UltimaMediaProvidersUtils.invokeExtractors
import com.admknight.ultima.UltimaUtils.Category
import com.admknight.ultima.UltimaUtils.LinkData

class Simkl(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "Simkl"
    override var mainUrl = "https://simkl.com"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Simkl)
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val repo = SyncRepo(AccountManager.simklApi)
    private val apiUrl = "https://api.simkl.com"
    private final val mediaLimit = 20

    protected fun Any.toStringData(): String = mapper.writeValueAsString(this)

    private fun SimklMediaObject.toSearchResponse(): SearchResponse {
        val poster = getPosterUrl(poster ?: "")
        return newMovieSearchResponse(title, "$mainUrl/shows/${ids?.simkl}", TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override val mainPage = mainPageOf(
        "$apiUrl/tv/trending/month?type=series&client_id=&extended=overview&limit=$mediaLimit&page=" to "Trending TV Shows",
        "$apiUrl/movies/trending/month?client_id=&extended=overview&limit=$mediaLimit&page=" to "Trending Movies",
        "$apiUrl/tv/best/all?type=series&client_id=&extended=overview&limit=$mediaLimit&page=" to "Best TV Shows",
        "Personal" to "Personal"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.name.contains("Personal")) {
            repo.authUser() ?: return newHomePageResponse("Login required.", emptyList(), false)
            val homePageList = repo.library().getOrThrow()?.allLibraryLists?.mapNotNull {
                if (it.items.isEmpty()) return@mapNotNull null
                HomePageList("${request.name}: ${it.name.asString(plugin.activity ?: return@mapNotNull null)}", it.items)
            } ?: return null
            return newHomePageResponse(homePageList, false)
        } else {
            val res = app.get("${request.data}$page").parsedSafe<Array<SimklMediaObject>>() ?: emptyArray()
            val media = res.map { it.toSearchResponse() }
            return newHomePageResponse(request.name, media, res.size == mediaLimit)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val data = app.get("$apiUrl/tv/$id?client_id=&extended=full").parsedSafe<SimklMediaObject>() ?: throw ErrorLoadingException("Failed to load data")
        val year = data.year
        val posterUrl = getPosterUrl(data.poster ?: "")
        
        return if (data.type == "movie") {
            newMovieLoadResponse(data.title, url, TvType.Movie, data.toLinkData().toStringData()) {
                this.addSimklId(id.toInt())
                this.year = year
                this.posterUrl = posterUrl
                this.plot = data.overview
                this.recommendations = data.recommendations?.map { it.toSearchResponse() }
            }
        } else {
            val eps = app.get("$apiUrl/tv/episodes/$id?client_id=&extended=full").parsedSafe<Array<SimklEpisodeObject>>() ?: emptyArray()
            val isAnime = data.type == "anime"
            val episodes = eps.filter { it.type == "episode" }.map { 
                it.toEpisode(data.title, data.ids, year, isAnime)
            }
            newTvSeriesLoadResponse(data.title, url, TvType.TvSeries, episodes) {
                this.addSimklId(id.toInt())
                this.year = year
                this.posterUrl = posterUrl
                this.plot = data.overview
                this.recommendations = data.recommendations?.map { it.toSearchResponse() }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val mediaData = parseJson<LinkData>(data)
        invokeExtractors(if (mediaData.isAnime) Category.ANIME else Category.MEDIA, mediaData, subtitleCallback, callback)
        return true
    }

    private fun SimklMediaObject.toLinkData(): LinkData = LinkData(simklId = ids?.simkl, imdbId = ids?.imdb, tmdbId = ids?.tmdb, malId = ids?.mal?.toIntOrNull(), title = title, year = year, type = type, isAnime = type == "anime")

    private fun SimklEpisodeObject.toEpisode(showName: String, ids: SimklIds?, year: Int?, isAnime: Boolean): Episode {
        val linkData = LinkData(simklId = ids?.simkl, imdbId = ids?.imdb, tmdbId = ids?.tmdb, aniId = ids?.anilist?.toIntOrNull(), malId = ids?.mal?.toIntOrNull(), title = showName, year = year, season = season, episode = episode, type = type, isAnime = isAnime).toStringData()
        return newEpisode(linkData) {
            this.name = title
            this.description = desc
            this.posterUrl = if (img != null) "https://simkl.in/episodes/${img}_c.webp" else null
            this.season = season
            this.episode = episode
        }
    }

    open class SimklMediaObject(
        @param:JsonProperty("title") val title: String,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("ids") val ids: SimklIds? = null,
        @param:JsonProperty("total_episodes") val total_episodes: Int? = null,
        @param:JsonProperty("poster") val poster: String? = null,
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("overview") val overview: String? = null,
        @param:JsonProperty("users_recommendations") val recommendations: List<SimklMediaObject>? = null,
    )

    open class SimklEpisodeObject(
        @param:JsonProperty("title") val title: String? = null,
        @param:JsonProperty("description") val desc: String? = null,
        @param:JsonProperty("season") val season: Int? = null,
        @param:JsonProperty("episode") val episode: Int? = null,
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("img") val img: String? = null,
    )

    data class SimklIds(
        @param:JsonProperty("simkl") val simkl: Int? = null,
        @param:JsonProperty("imdb") val imdb: String? = null,
        @param:JsonProperty("tmdb") val tmdb: Int? = null,
        @param:JsonProperty("mal") val mal: String? = null,
        @param:JsonProperty("anilist") val anilist: String? = null,
    )
}




