package com.admknight.ultima

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.admknight.ultima.UltimaMediaProvidersUtils.invokeExtractors
import com.admknight.ultima.UltimaUtils.Category
import com.admknight.ultima.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.api.Log
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.syncproviders.SyncRepo
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.CoverImage
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.LikePageInfo
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.RecommendationConnection
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.SeasonNextAiringEpisode
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Title
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.ArrayList

class AniList(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "AniList"
    override var mainUrl = "https://anilist.co"
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Anilist)
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val repo = SyncRepo(AccountManager.aniListApi)
    private val apiUrl = "https://graphql.anilist.co"
    private final val mediaLimit = 20
    private final val isAdult = false
    private val headerJSON = mapOf("Accept" to "application/json", "Content-Type" to "application/json")

    protected fun Any.toStringData(): String = mapper.writeValueAsString(this)

    private suspend fun anilistAPICall(query: String): AnilistAPIResponse {
        val data = mapOf("query" to query)
        val res = app.post(apiUrl, headers = headerJSON, data = data).parsedSafe<AnilistAPIResponse>() ?: throw Exception("Unable to fetch AniList API response")
        return res
    }

    private fun AniListApi.Media.toSearchResponse(): SearchResponse {
        val title = this.title.english ?: this.title.romaji ?: ""
        val url = "$mainUrl/anime/${this.id}"
        val posterUrl = this.coverImage.large
        return newAnimeSearchResponse(title, url, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override val mainPage = mainPageOf(
        "query (\$page: Int = ###, \$sort: [MediaSort] = [TRENDING_DESC, POPULARITY_DESC], \$isAdult: Boolean = $isAdult) { Page(page: \$page, perPage: $mediaLimit) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters averageScore title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to "Trending Now",
        "Personal" to "Personal"
    )

    override suspend fun search(query: String): List<SearchResponse>? {
        val res = anilistAPICall("query (\$search: String = \"$query\") { Page(page: 1, perPage: $mediaLimit) { pageInfo { total perPage currentPage lastPage hasNextPage } media(search: \$search, isAdult: $isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }")
        return res.data.page?.media?.map { it.toSearchResponse() }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.name.contains("Personal")) {
            repo.authUser() ?: return newHomePageResponse("Login required.", emptyList(), false)
            val homePageList = repo.library().getOrThrow()?.allLibraryLists?.mapNotNull {
                if (it.items.isEmpty()) return@mapNotNull null
                HomePageList("${request.name}: ${it.name.asString(plugin.activity ?: return@mapNotNull null)}", it.items)
            } ?: return null
            return newHomePageResponse(homePageList, false)
        } else {
            val query = request.data.replace("###", page.toString())
            val res = anilistAPICall(query)
            val data = res.data.page?.media?.map { it.toSearchResponse() } ?: emptyList()
            return newHomePageResponse(request.name, data, res.data.page?.pageInfo?.hasNextPage ?: false)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.removeSuffix("/").substringAfterLast("/")
        val data = anilistAPICall("query (\$id: Int = $id) { Media(id: \$id, type: ANIME) { id title { romaji english } startDate { year } genres description averageScore bannerImage coverImage { extraLarge large medium } episodes format nextAiringEpisode { episode } airingSchedule { nodes { episode } } recommendations { edges { node { id mediaRecommendation { id title { romaji english } coverImage { extraLarge large medium } } } } } } }").data.media ?: throw Exception("Unable to fetch details")

        val anititle = data.getTitle()
        val aniyear = data.startDate.year
        val isMovie = data.format?.contains("MOVIE", true) == true
        
        val syncMetaData = try { app.get("https://api.ani.zip/mappings?anilist_id=$id").text } catch(e: Exception) { "" }
        val animeMetaData = parseAnimeDataLocal(syncMetaData)

        val totalEpisodes = data.totalEpisodes()
        val episodes = (1..totalEpisodes).map { i ->
            val epData = animeMetaData?.episodes?.get(i.toString())
            val linkData = LinkData(title = anititle, year = aniyear, season = 1, episode = i, isAnime = true).toStringData()
            newEpisode(linkData) {
                this.season = 1
                this.episode = i
                this.name = epData?.title?.get("en") ?: "Episode $i"
                this.posterUrl = epData?.image ?: data.getCoverImage()
                this.description = epData?.overview
                addDate(epData?.airDateUtc)
            }
        }

        return if (isMovie) {
            newMovieLoadResponse(anititle, url, TvType.AnimeMovie, LinkData(title = anititle, year = aniyear, isAnime = true).toStringData()) {
                addAniListId(id.toInt())
                this.year = aniyear
                this.plot = data.description
                this.backgroundPosterUrl = data.bannerImage
                this.posterUrl = data.getCoverImage()
                this.tags = data.genres
            }
        } else {
            newAnimeLoadResponse(anititle, url, TvType.Anime) {
                addAniListId(id.toInt())
                addEpisodes(DubStatus.Subbed, episodes)
                this.year = aniyear
                this.plot = data.description
                this.backgroundPosterUrl = data.bannerImage
                this.posterUrl = data.getCoverImage()
                this.tags = data.genres
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val mediaData = AppUtils.parseJson<LinkData>(data)
        invokeExtractors(Category.ANIME, mediaData, subtitleCallback, callback)
        return true
    }

    data class AnilistAPIResponse(val data: AnilistData)
    data class AnilistData(val Page: AnilistPage?, val Media: anilistMedia?)
    data class AnilistPage(val pageInfo: LikePageInfo, val media: List<AniListApi.Media>)
    data class anilistMedia(val id: Int, val startDate: StartDate, val episodes: Int?, val title: Title, val genres: List<String>, val description: String?, val coverImage: CoverImage, val bannerImage: String?, val nextAiringEpisode: SeasonNextAiringEpisode?, val airingSchedule: AiringScheduleNodes?, val format: String?) {
        data class StartDate(val year: Int)
        data class AiringScheduleNodes(val nodes: List<SeasonNextAiringEpisode>?)
        fun totalEpisodes(): Int = nextAiringEpisode?.episode?.minus(1) ?: episodes ?: airingSchedule?.nodes?.getOrNull(0)?.episode ?: 0
        fun getTitle(): String = title.english ?: title.romaji ?: ""
        fun getCoverImage(): String? = coverImage.extraLarge ?: coverImage.large ?: coverImage.medium
    }
}

fun parseAnimeDataLocal(jsonString: String): MetaAnimeDataLocal? = try { ObjectMapper().readValue(jsonString, MetaAnimeDataLocal::class.java) } catch (_: Exception) { null }

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeDataLocal(val titles: Map<String, String>? = null, val episodes: Map<String, MetaEpisodeLocal>? = null, val mappings: MetaMappingsLocal? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisodeLocal(val title: Map<String, String>? = null, val image: String? = null, val overview: String? = null, val airDateUtc: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaMappingsLocal(val kitsuid: String? = null)





