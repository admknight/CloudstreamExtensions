package com.admknight.doramasflix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class DoramasFlixProvider : MainAPI() {
    companion object {
        private const val doraflixapi = "https://doraflix.fluxcedene.net/api/gql"
        private val mediaType = "application/json; charset=utf-8".toMediaType()
    }

    override var mainUrl = "https://doramasflix.co"
    override var name = "Doramasflix"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    data class MainDoramas (@JsonProperty("data") var data : DataDoramas? = DataDoramas())
    data class DataDoramas (
        @JsonProperty("listDoramas") var listDoramas : ArrayList<ListDoramas>? = arrayListOf(),
        @JsonProperty("searchDorama") var searchDorama : ArrayList<ListDoramas>? = arrayListOf(),
        @JsonProperty("searchMovie") var searchMovie : ArrayList<ListDoramas>? = arrayListOf(),
        @JsonProperty("listSeasons") var listSeasons : ArrayList<ListDoramas>? = arrayListOf(),
        @JsonProperty("detailDorama") var detailDorama : DetailDoramaandDoramaMeta? = DetailDoramaandDoramaMeta(),
        @JsonProperty("detailMovie") var detailMovie : DetailDoramaandDoramaMeta? = DetailDoramaandDoramaMeta(),
        @JsonProperty("paginationEpisode") var paginationEpisode : PaginationEpisode? = PaginationEpisode(),
        @JsonProperty("detailEpisode") var detailEpisode : DetailDoramaandDoramaMeta? = DetailDoramaandDoramaMeta(),
        @JsonProperty("paginationDorama") var paginationDorama : ListDoramasWrapper? = ListDoramasWrapper(),
        @JsonProperty("paginationMovie") var paginationMovie : ListDoramasWrapper? = ListDoramasWrapper()
    )

    data class ListDoramasWrapper (
        @JsonProperty("items") var items : ArrayList<ListDoramas>? = arrayListOf()
    )

    data class ListDoramas (
        @JsonProperty("_id") var Id : String? = null,
        @JsonProperty("name") var name : String? = null,
        @JsonProperty("slug") var slug : String? = null,
        @JsonProperty("poster_path") var posterPath : String? = null,
        @JsonProperty("isTVShow") var isTVShow : Boolean? = null,
        @JsonProperty("__typename") var _typename : String? = null,
        @JsonProperty("season_number") var seasonNumber : Int? = null,
    )

    data class DetailDoramaandDoramaMeta (
        @JsonProperty("_id") var Id : String? = null,
        @JsonProperty("name") var name : String? = null,
        @JsonProperty("slug") var slug : String? = null,
        @JsonProperty("overview") var overview : String? = null,
        @JsonProperty("poster_path") var posterPath : String? = null,
        @JsonProperty("backdrop_path") var backdropPath : String? = null,
        @JsonProperty("poster") var poster : String? = null,
        @JsonProperty("backdrop") var backdrop : String? = null,
        @JsonProperty("genres") var genres : ArrayList<GenresAndLabels>? = arrayListOf(),
        @JsonProperty("labels") var labels : ArrayList<GenresAndLabels>? = arrayListOf(),
        @JsonProperty("links_online") var linksOnline : ArrayList<LinksOnline>? = arrayListOf(),
        @JsonProperty("still_path") var stillPath : String? = null,
        @JsonProperty("episode_number") var episodeNumber : Int? = null,
        @JsonProperty("season_number") var seasonNumber : Int? = null,
    )

    data class LinksOnline (
        @JsonProperty("server") var server : String? = null,
        @JsonProperty("link") var link : String? = null,
    )

    data class GenresAndLabels (
        @JsonProperty("name") var name : String? = null,
    )

    data class DoramasInfo (
        @JsonProperty("id") var id : String? = null,
        @JsonProperty("slug") var slug : String? = null,
        @JsonProperty("type") var type : String? = null,
        @JsonProperty("isTV") var isTV : Boolean? = null
    )

    data class PaginationEpisode (
        @JsonProperty("items") var items : ArrayList<DetailDoramaandDoramaMeta> = arrayListOf(),
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w1280/$link" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = ArrayList<HomePageList>()
        val doramasBody = "{\"operationName\":\"listDoramasMobile\",\"variables\":{\"filter\":{\"isTVShow\":false},\"limit\":32,\"sort\":\"_ID_DESC\"},\"query\":\"query listDoramasMobile(\$limit: Int, \$skip: Int, \$sort: SortFindManyDoramaInput, \$filter: FilterFindManyDoramaInput) {\\n  listDoramas(limit: \$limit, skip: \$skip, sort: \$sort, filter: \$filter) {\\n    _id\\n    name\\n    name_es\\n    slug\\n    poster_path\\n    isTVShow\\n    poster\\n    __typename\\n  }\\n}\\n\"}"
        val peliculasBody = "{\"operationName\":\"paginationMovie\",\"variables\":{\"perPage\":32,\"sort\":\"CREATEDAT_DESC\",\"filter\":{},\"page\":1},\"query\":\"query paginationMovie(\$page: Int, \$perPage: Int, \$sort: SortFindManyMovieInput, \$filter: FilterFindManyMovieInput) {\\n  paginationMovie(page: \$page, perPage: \$perPage, sort: \$sort, filter: \$filter) {\\n    items {\\n      _id\\n      name\\n      name_es\\n      slug\\n      poster_path\\n      poster\\n      __typename\\n    }\\n  }\\n}\\n\"}"
        
        val doraResponse = app.post(doraflixapi, requestBody = doramasBody.toRequestBody(mediaType)).parsed<MainDoramas>()
        val pelisResponse = app.post(doraflixapi, requestBody = peliculasBody.toRequestBody(mediaType)).parsed<MainDoramas>()
        
        doraResponse.data?.listDoramas?.let { list ->
            items.add(HomePageList("Doramas", list.map { tasa(it) }))
        }
        pelisResponse.data?.paginationMovie?.items?.let { list ->
            items.add(HomePageList("Películas", list.map { tasa(it) }))
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    private fun tasa(info: ListDoramas): SearchResponse {
        val data = "{\"id\":\"${info.Id}\",\"slug\":\"${info.slug}\",\"type\":\"${info._typename}\",\"isTV\":${info.isTVShow}}"
        return newMovieSearchResponse(info.name ?: "", data, TvType.AsianDrama) {
            this.posterUrl = getImageUrl(info.posterPath)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body = "{\"operationName\":\"searchAll\",\"variables\":{\"input\":\"$query\"},\"query\":\"query searchAll(\$input: String!) {\\n  searchDorama(input: \$input, limit: 5) {\\n    _id\\n    slug\\n    name\\n    name_es\\n    poster_path\\n  isTVShow\\n  poster\\n    __typename\\n  }\\n  searchMovie(input: \$input, limit: 5) {\\n    _id\\n    name\\n    name_es\\n    slug\\n    poster_path\\n    poster\\n    __typename\\n  }\\n}\\n\"}"
        val response = app.post(doraflixapi, requestBody = body.toRequestBody(mediaType)).parsed<MainDoramas>()
        val results = ArrayList<SearchResponse>()
        response.data?.searchDorama?.forEach { results.add(tasa(it)) }
        response.data?.searchMovie?.forEach { results.add(tasa(it)) }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val parse = try { parseJson<DoramasInfo>(url) } catch (_: Exception) { return null }
        val isMovie = !parse.type!!.contains("Dorama")
        
        val body = if (isMovie) {
            "{\"operationName\":\"detailMovieExtra\",\"variables\":{\"slug\":\"${parse.slug}\"},\"query\":\"query detailMovieExtra(\$slug: String!) {\\n  detailMovie(filter: {slug: \$slug}) {\\n    name\\n    name_es\\n    overview\\n    languages\\n    popularity\\n  poster_path\\n poster\\n  backdrop_path\\n    backdrop\\n    links_online\\n    __typename\\n genres {\\n      name\\n      slug\\n      __typename\\n    }\\n  }\\n}\\n\"}"
        } else {
            "{\"operationName\":\"detailDorama\",\"variables\":{\"slug\":\"${parse.slug}\"},\"query\":\"query detailDorama(\$slug: String!) {\\n  detailDorama(filter: {slug: \$slug}) {\\n    _id\\n    name\\n    slug\\n    overview\\n    poster_path\\n    backdrop_path\\n    poster\\n    backdrop\\n    genres {\\n      name\\n    }\\n    labels {\\n      name\\n    }\\n    __typename\\n  }\\n}\\n\"}"
        }
        
        val meta = app.post(doraflixapi, requestBody = body.toRequestBody(mediaType)).parsed<MainDoramas>()
        val info = if (isMovie) meta.data?.detailMovie else meta.data?.detailDorama
        val title = info?.name ?: ""
        val poster = getImageUrl(info?.poster ?: info?.posterPath)
        val bgPoster = getImageUrl(info?.backdrop ?: info?.backdropPath)
        val tags = ArrayList<String>()
        info?.genres?.forEach { it.name?.let { n -> tags.add(it.name!!) } }
        info?.labels?.forEach { it.name?.let { n -> tags.add(it.name!!) } }
        
        val episodes = ArrayList<Episode>()
        var movieData: String? = null

        if (!isMovie) {
            val sBody = "{\"operationName\":\"listSeasons\",\"variables\":{\"serie_id\":\"${parse.id}\"},\"query\":\"query listSeasons(\$serie_id: MongoID!) {\\n  listSeasons(sort: NUMBER_ASC, filter: {serie_id: \$serie_id}) {\\n    slug\\n    season_number\\n  }\\n}\\n\"}"
            val sRes = app.post(doraflixapi, requestBody = sBody.toRequestBody(mediaType)).parsed<MainDoramas>()
            sRes.data?.listSeasons?.forEach { s ->
                val epBody = "{\"operationName\":\"listEpisodesPagination\",\"variables\":{\"serie_id\":\"${parse.id}\",\"season_number\":${s.seasonNumber},\"page\":1},\"query\":\"query listEpisodesPagination(\$page: Int!, \$serie_id: MongoID!, \$season_number: Float!) {\\n  paginationEpisode(\\n    page: \$page\\n    perPage: 1000\\n    sort: NUMBER_ASC\\n    filter: {type_serie: \\\"dorama\\\", serie_id: \$serie_id, season_number: \$season_number}\\n  ) {\\n       items {\\n      name\\n      episode_number\\n      season_number\\n      slug\\n    }\\n  }\\n}\\n\"}"
                val epRes = app.post(doraflixapi, requestBody = epBody.toRequestBody(mediaType)).parsed<MainDoramas>()
                epRes.data?.paginationEpisode?.items?.forEach { e ->
                    episodes.add(newEpisode(e.slug!!) {
                        this.name = e.name
                        this.season = e.seasonNumber
                        this.episode = e.episodeNumber
                    })
                }
            }
        } else {
            movieData = info?.linksOnline?.toJson()
        }

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, movieData!!) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.plot = info?.overview
                this.tags = tags.distinct()
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.plot = info?.overview
                this.tags = tags.distinct()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.contains("link")) {
            val parse = try { parseJson<List<LinksOnline>>(data) } catch (_: Exception) { emptyList() }
            parse.forEach { it.link?.let { l -> loadExtractor(l, data, subtitleCallback, callback) } }
        } else {
            val body = "{\"operationName\":\"GetEpisodeLinks\",\"variables\":{\"episode_slug\":\"$data\"},\"query\":\"query GetEpisodeLinks(\$episode_slug: String!) {\\n  detailEpisode(filter: {slug: \$episode_slug, type_serie: \\\"dorama\\\"}) {\\n    links_online\\n   }\\n}\\n\"}"
            val res = app.post(doraflixapi, requestBody = body.toRequestBody(mediaType)).parsedSafe<MainDoramas>()
            res?.data?.detailEpisode?.linksOnline?.forEach {
                val link = it.link?.replace("https://swdyu.com", "https://streamwish.to")?.replace("https://uqload.to", "https://uqload.co")
                if (link != null) loadExtractor(link, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
