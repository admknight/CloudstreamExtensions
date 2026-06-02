package com.admknight.showflix

import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ShowFlixProvider : MainAPI() {
    override var mainUrl = "https://showflix.store"
    override var name = "ShowFlix"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val installationID = "60f6b1a7-8860-4edf-b255-6bc465b6c704"

    data class TVAll(@JsonProperty("results") var results: List<TVResult> = listOf())
    data class TVResult(
        @JsonProperty("objectId") var objectId: String,
        @JsonProperty("name") var name: String,
        @JsonProperty("posterURL") var posterURL: String?,
        @JsonProperty("seriesCategory") var seriesCategory: String?,
        @JsonProperty("rating") var rating: String?,
        @JsonProperty("backdropURL") var backdropURL: String?,
        @JsonProperty("storyline") var storyline: String?
    )

    data class MovieAll(@JsonProperty("results") var results: List<MovieResults> = emptyList())
    data class MovieResults(
        @JsonProperty("objectId") val objectId: String? = null,
        @JsonProperty("name") val name: String,
        @JsonProperty("posterURL") val posterURL: String? = null,
        @JsonProperty("backdropURL") val backdropURL: String? = null,
        @JsonProperty("storyline") val storyline: String? = null,
        @JsonProperty("rating") val rating: String? = null,
        @JsonProperty("embedLinks") val embedLinks: EmbedLinks? = null,
        @JsonProperty("hdLink") val hdLink: String? = null,
        @JsonProperty("hubCloudLink") val hubCloudLink: String? = null,
        @JsonProperty("originalURL") val originalURL: String? = null,
        @JsonProperty("goFile") val goFile: String? = null,
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("drive") val drive: String? = null,
    )

    data class MovieLinks(
        val streamruby: String? = null,
        val upnshare: String? = null,
        val streamwish: String? = null,
        val vihide: String? = null,
        val hdlink: String? = null,
        val originalURL: String? = null,
        val drive: String? = null,
        val goFile: String? = null,
        val hubCloudLink: String? = null
    )

    private val MovieapiUrl = "https://parse.showflix.sbs/parse/classes/moviesv2"
    private val TVapiUrl    = "https://parse.showflix.sbs/parse/classes/seriesv2"
    private val Api = "https://parse.showflix.sbs/parse/classes"

    private suspend fun queryMovieApi(query: String): NiceResponse {
        val req = (if (query.isBlank()) """{"where":{},"limit":20,"order":"-createdAt","count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""" else """{"where":{"languages":{"${"$"}in":["$query"]}},"limit":20,"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""").toRequestBody("text/plain".toMediaTypeOrNull())
        return app.post(MovieapiUrl, requestBody = req, referer = "$mainUrl/")
    }

    private suspend fun queryTVApi(query: String): NiceResponse {
        val req = (if (query.isBlank()) """{"where":{},"limit":20,"order":"-createdAt","count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""" else """{"where":{"languages":{"${"$"}in":["$query"]}},"limit":20,"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""").toRequestBody("text/plain".toMediaTypeOrNull())
        return app.post(TVapiUrl, requestBody = req, referer = "$mainUrl/")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val elements = ArrayList<HomePageList>()
        if (request.name.contains("Movies")) {
            val res = queryMovieApi(request.data).parsed<MovieAll>().results
            val home = res.map {
                newMovieSearchResponse(it.name, "$mainUrl/movie/${it.objectId}", TvType.Movie) {
                    this.posterUrl = it.posterURL
                    this.quality = SearchQuality.HD
                }
            }
            elements.add(HomePageList(request.name, home))
        } else {
            val res = queryTVApi(request.data).parsed<TVAll>().results
            val home = res.map {
                newTvSeriesSearchResponse(it.name, "$mainUrl/series/${it.objectId}", TvType.TvSeries) {
                    this.posterUrl = it.posterURL
                    this.quality = SearchQuality.HD
                }
            }
            elements.add(HomePageList(request.name, home))
        }
        return newHomePageResponse(elements, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body = """{"where":{"name":{"${"$"}regex":"$query","${"$"}options":"i"}},"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        val movieRes = app.post(MovieapiUrl, requestBody = body, referer = "$mainUrl/").parsed<MovieAll>().results
        val tvRes = app.post(TVapiUrl, requestBody = body, referer = "$mainUrl/").parsed<TVAll>().results

        val movies = movieRes.map {
            newMovieSearchResponse(it.name, "$mainUrl/movie/${it.objectId}", TvType.Movie) {
                this.posterUrl = it.posterURL
                this.quality = SearchQuality.HD
            }
        }
        val tvs = tvRes.map {
            newTvSeriesSearchResponse(it.name, "$mainUrl/series/${it.objectId}", TvType.TvSeries) {
                this.posterUrl = it.posterURL
                this.quality = SearchQuality.HD
            }
        }
        return (movies + tvs).sortedBy { -FuzzySearch.partialRatio(it.name.lowercase(), query.lowercase()) }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.contains("/movie/")) {
            val objId = url.substringAfterLast("/")
            val body = """{"where":{"objectId":"$objId"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody("text/plain".toMediaTypeOrNull())
            val res = app.post(MovieapiUrl, requestBody = body, referer = "$mainUrl/").parsed<MovieAll>().results.first()

            val title = res.name
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            val scoreVal = Score.from10(res.rating)

            val recs = queryMovieApi("").parsed<MovieAll>().results.map {
                newMovieSearchResponse(it.name, "$mainUrl/movie/${it.objectId}", TvType.Movie) {
                    this.posterUrl = it.posterURL
                    this.quality = SearchQuality.HD
                }
            }

            return newMovieLoadResponse(title, url, TvType.Movie, MovieLinks(
                res.embedLinks?.streamruby, res.embedLinks?.upnshare, res.embedLinks?.streamwish, res.embedLinks?.vihide,
                res.hdLink, res.originalURL, res.drive, res.goFile, res.hubCloudLink
            ).toJson()) {
                this.posterUrl = res.posterURL
                this.year = year
                this.plot = res.storyline
                this.score = scoreVal
                this.backgroundPosterUrl = res.backdropURL
                this.recommendations = recs
            }
        } else {
            val objId = url.substringAfterLast("/")
            val body = """{"where":{"objectId":"$objId"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody("text/plain".toMediaTypeOrNull())
            val res = app.post(TVapiUrl, requestBody = body, referer = "$mainUrl/").parsed<TVAll>().results.first()
            
            val title = res.name
            val year = Regex("\\d{4}").find(title)?.value?.toIntOrNull()
            val scoreVal = Score.from10(res.rating)

            val recs = queryTVApi("").parsed<TVAll>().results.map {
                newTvSeriesSearchResponse(it.name, "$mainUrl/series/${it.objectId}", TvType.TvSeries) {
                    this.posterUrl = it.posterURL
                    this.quality = SearchQuality.HD
                }
            }

            val seasons = getSeasonsWithEpisodes(res.objectId)
            val episodes = seasons.flatMap { (sName, eps) ->
                val sNum = Regex("\\d+").find(sName)?.value?.toIntOrNull()
                eps.map { e ->
                    newEpisode(MovieLinks(e.embedLinks?.streamruby, e.embedLinks?.upnshare, e.embedLinks?.streamwish, e.embedLinks?.vihide).toJson()) {
                        this.season = sNum
                        this.episode = e.episodeNumber
                        this.posterUrl = res.backdropURL
                    }
                }
            }.filter { it.episode != 0 }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = res.posterURL
                this.year = year
                this.plot = res.storyline
                this.score = scoreVal
                this.backgroundPosterUrl = res.backdropURL
                this.recommendations = recs
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = try { parseJson<MovieLinks>(data) } catch (_: Exception) { return false }
        val urls = listOfNotNull(
            links.streamwish?.let { "https://embedwish.com/e/$it" },
            links.streamruby?.let { "https://rubyvidhub.com/embed-$it.html" },
            links.upnshare?.let { "https://showflix.upns.one/#$it" },
            links.vihide?.let { "https://smoothpre.com/v/$it.html" },
            links.originalURL,
            links.hdlink
        )
        
        urls.amap { iframe ->
            if (iframe.endsWith(".mkv")) {
                callback.invoke(newExtractorLink(name, name, iframe, INFER_TYPE) {
                    this.quality = Qualities.P1080.value
                })
            } else {
                loadExtractor(iframe, subtitleCallback, callback)
            }
        }
        return true
    }

    private suspend fun getSeasonsWithEpisodes(seriesId: String): List<Pair<String, List<EpisodeDetails>>> {
        val body = """{"where":{"seriesId":"$seriesId"},"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody("text/plain".toMediaTypeOrNull())
        val sRes = app.post("$Api/seasonv2", requestBody = body, referer = "$mainUrl/").parsed<SeasonResult>()
        
        return sRes.results.map { s ->
            val eBody = """{"where":{"seasonId":"${s.objectId}"},"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody("text/plain".toMediaTypeOrNull())
            val eRes = app.post("$Api/episodev2", requestBody = eBody, referer = "$mainUrl/").parsed<EpisodeResult>()
            s.name to eRes.results
        }
    }
}
