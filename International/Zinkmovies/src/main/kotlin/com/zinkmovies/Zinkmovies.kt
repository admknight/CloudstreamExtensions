package com.zinkmovies

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.text.Normalizer

class Zinkmovies : MainAPI() {
    override var mainUrl: String = runBlocking {
        ZinkmoviesPlugin.getDomains()?.zinkmovies ?: "https://new7.zinkmovies.biz"
    }
    override var name = "Zinkmovies"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object {
        const val TMDBAPIKEY = "1865f43a0549ca50d341dd9ab8b29f49"
        const val TMDBBASE = "https://image.tmdb.org/t/p/original"
        const val TMDBAPI = "https://api.themoviedb.org/3"
    }

    override val mainPage = mainPageOf(
        "" to "Home",
        "movies/" to "Movies",
        "tvshows/" to "Tv Shows",
        "genre/bollywood/" to "Bollywood",
        "genre/HOLLYWOOD-MOVIES/" to "Hollywood",
        "genre/animation/" to "Animation",
        "genre/anime/" to "Anime",
        "genre/korean/" to "KDrama",
    )
    private val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0","Cookie" to "xla=s4t")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/${request.data}page/$page/", headers = headers).document
        val home = doc.select("article").filterNot { it.closest(".animation-1") != null || it.closest(".items.featured") != null }
            .mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val titleText = post.selectFirst("h3 a")?.text() ?: ""
        val title = cleanTitle(titleText)
        val url = post.selectFirst("h3 a")?.attr("href") ?: ""
        val poster = post.selectFirst("img")?.attr("data-lazy-src")?.ifBlank { post.selectFirst("img")?.attr("src") } ?: ""
        val scoreValue = Score.from10(post.selectFirst("div.rating")?.text())
        
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster.replace("/w185/", "/w500/")
            this.score = scoreValue
            this.quality = getSearchQuality(post.selectFirst("span.quality")?.text())
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val response = app.get("$mainUrl/page/$page/?s=$query").document.select("article")
        return response.map {
            val sName = it.selectFirst("a")?.text() ?: ""
            val href = it.selectFirst("a")?.attr("href") ?: ""
            val poster = it.selectFirst("img")?.attr("data-lazy-src")?.ifBlank { it.selectFirst("img")?.attr("src") } ?: ""
            newMovieSearchResponse(sName, href, TvType.Movie) {
                this.posterUrl = poster.replace("/w92/", "/w500/")
            }
        }.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document
        val hTitle = doc.selectFirst("div.sheader h1")?.text() ?: ""
        var title = hTitle.substringBefore("(")
        val seasonNumber = Regex("(?i)\\bSeason\\s*(\\d+)\\b").find(hTitle)?.groupValues?.get(1)?.toIntOrNull()

        val image = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val tags = doc.select("div.sgeneros a").eachText().toMutableList()
        val poster = doc.selectFirst("img")?.attr("data-lazy-src")?.ifBlank { doc.selectFirst("img")?.attr("src") }?.replace("/w185/", "/w500/") ?: ""
        val trailer = doc.selectFirst(".responsive-embed-container iframe")?.attr("src")?.replace("/embed/", "/watch?v=")

        val tvtype = if (url.contains("/tvshows/", ignoreCase = true)) TvType.TvSeries else TvType.Movie

        val recommendations = doc.select("#single_relacionados article").map {
            val rPoster = it.selectFirst("img")?.attr("data-lazy-src")?.ifBlank { it.selectFirst("img")?.attr("src") } ?: ""
            val rHref = it.selectFirst("a")?.attr("href") ?: ""
            newMovieSearchResponse("", rHref, TvType.Movie) { this.posterUrl = rPoster }
        }

        var tmdbIdResolved = ""
        runCatching {
            val query = title.substringBefore("(").replace("Season $seasonNumber", "", ignoreCase = true).trim()
            val type = if (tvtype == TvType.TvSeries) "tv" else "movie"
            val searchJson = JSONObject(app.get("$TMDBAPI/search/$type?api_key=$TMDBAPIKEY&query=$query").text)
            tmdbIdResolved = searchJson.optJSONArray("results")?.optJSONObject(0)?.optInt("id")?.toString().orEmpty()
        }

        val responseData: ResponseDataLocal? = if (tmdbIdResolved.isBlank()) null else runCatching {
            val type = if (tvtype == TvType.TvSeries) "tv" else "movie"
            val detailsJson = JSONObject(app.get("$TMDBAPI/$type/$tmdbIdResolved?api_key=$TMDBAPIKEY&append_to_response=credits,external_ids").text)

            var metaName = detailsJson.optString("name").ifBlank { detailsJson.optString("title") }.ifBlank { title }
            if (seasonNumber != null && !metaName.contains("Season $seasonNumber", ignoreCase = true)) {
                metaName = "$metaName (Season $seasonNumber)"
            }

            val metaDesc = detailsJson.optString("overview").ifBlank { plot }
            val yearRaw = detailsJson.optString("release_date").ifBlank { detailsJson.optString("first_air_date") }
            val metaYear = yearRaw.take(4)
            val metaRating = Score.from10(detailsJson.optString("vote_average"))
            val metaBackground = detailsJson.optString("backdrop_path").let { if (it.isNotBlank()) TMDBBASE + it else image }
            val imdbId = detailsJson.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.isNotBlank() }
            val logoPath = imdbId?.let { "https://live.metahub.space/logo/medium/$it/img" }

            val actorDataList = mutableListOf<ActorData>()
            detailsJson.optJSONObject("credits")?.optJSONArray("cast")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val aName = c.optString("name").ifBlank { c.optString("original_name") }
                    val aProfile = c.optString("profile_path").let { if (it.isNotBlank()) TMDBBASE + it else null }
                    actorDataList += ActorData(Actor(aName, aProfile), roleString = c.optString("character"))
                }
            }

            val metaGenres = mutableListOf<String>()
            detailsJson.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(metaGenres::add)
                }
            }

            val videos = mutableListOf<VideoLocal>()
            if (tvtype == TvType.TvSeries) {
                val totalSeasons = detailsJson.optInt("number_of_seasons")
                for (s in 1..totalSeasons) {
                    runCatching {
                        val sJson = JSONObject(app.get("$TMDBAPI/tv/$tmdbIdResolved/season/$s?api_key=$TMDBAPIKEY").text)
                        sJson.optJSONArray("episodes")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val ep = arr.optJSONObject(i) ?: continue
                                videos.add(VideoLocal(
                                    title = ep.optString("name"),
                                    season = s,
                                    episode = ep.optInt("episode_number"),
                                    overview = ep.optString("overview"),
                                    thumbnail = ep.optString("still_path").let { if (it.isNotBlank()) TMDBBASE + it else null },
                                    released = ep.optString("air_date"),
                                    rating = Score.from10(ep.optString("vote_average"))
                                ))
                            }
                        }
                    }
                }
            }

            ResponseDataLocal(MetaLocal(
                name = metaName,
                description = metaDesc,
                actorsData = actorDataList,
                year = metaYear,
                background = metaBackground,
                genres = metaGenres,
                videos = videos,
                rating = metaRating,
                logo = logoPath,
                imdbId = imdbId
            ))
        }.getOrNull()

        val meta = responseData?.meta
        val description = meta?.description ?: plot
        val actorData = meta?.actorsData ?: emptyList()
        title = meta?.name ?: title
        val yearVal = meta?.year ?: ""
        val background = meta?.background ?: image
        meta?.genres?.forEach { if (!tags.contains(it)) tags.add(it) }

        if (tvtype == TvType.Movie) {
            val movieList = doc.select("div.movie-button-container a").map { it.attr("href") }
            return newMovieLoadResponse(title, url, TvType.Movie, movieList) {
                this.backgroundPosterUrl = background
                this.recommendations = recommendations
                this.logoUrl = meta?.logo
                this.posterUrl = poster
                this.year = yearVal.toIntOrNull()
                this.plot = description
                this.tags = tags
                this.actors = actorData
                this.score = meta?.rating
                addTrailer(trailer)
                addImdbId(meta?.imdbId)
            }
        } else {
            val episodesData = mutableListOf<Episode>()
            val epLinksMap = mutableMapOf<Pair<Int, Int>, MutableList<String>>()
            val seasonRegex = Regex("Season\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val episodeRegex = Regex("EPISODE\\s*[-:]?\\s*(\\d+)", RegexOption.IGNORE_CASE)

            doc.select(".lgtagmessage").forEach { seasonElement ->
                val sNum = seasonRegex.find(seasonElement.text())?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                var next = seasonElement.nextElementSibling()
                while (next != null && !next.hasClass("lgtagmessage")) {
                    if (next.hasClass("movie-button-container")) {
                        val seasonUrl = next.selectFirst("a[href]")?.attr("href")?.trim()
                        if (!seasonUrl.isNullOrBlank()) {
                            runCatching {
                                val sDoc = app.get(seasonUrl).document
                                sDoc.select(".entry-content a[href]").forEach { ep ->
                                    val text = ep.text()
                                    val eNum = episodeRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()
                                    val href = ep.attr("href").trim()
                                    if (eNum != null && href.isNotBlank() && !text.contains("zip", true)) {
                                        epLinksMap.getOrPut(sNum to eNum) { mutableListOf() }.add(href)
                                    }
                                }
                            }
                        }
                    }
                    next = next.nextElementSibling()
                }
            }

            epLinksMap.forEach { (key, links) ->
                val (sNum, eNum) = key
                val info = meta?.videos?.find { it.season == sNum && it.episode == eNum }
                episodesData.add(newEpisode(links.distinct().toJson()) {
                    this.name = info?.title ?: "Episode $eNum"
                    this.season = sNum
                    this.episode = eNum
                    this.posterUrl = info?.thumbnail
                    this.description = info?.overview
                    this.score = info?.rating
                    addDate(info?.released)
                })
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.backgroundPosterUrl = background
                this.recommendations = recommendations
                this.logoUrl = meta?.logo
                this.posterUrl = poster
                this.year = yearVal.toIntOrNull()
                this.plot = description
                this.tags = tags
                this.actors = actorData
                this.score = meta?.rating
                addTrailer(trailer)
                addImdbId(meta?.imdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linksList = tryParseJson<List<String>>(data)?.filter { it.isNotBlank() } ?: emptyList()
        if (linksList.isEmpty()) return false

        linksList.amap { pageUrl ->
            generateZinkLinks(pageUrl).forEach { link ->
                if (link.name.contains("worker", true)) {
                    callback(newExtractorLink("Zink Worker", "Zink Worker", link.url, INFER_TYPE) {
                        this.quality = getIndexQuality(link.url)
                    })
                } else {
                    loadExtractor(link.url, name, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    fun getSearchQuality(check: String?): SearchQuality? {
        val s = check ?: return null
        val u = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val patterns = listOf(
            Regex("\\b(hdts|hdcam|hdtc)\\b") to SearchQuality.HdCam,
            Regex("\\b(camrip|cam[- ]?rip)\\b") to SearchQuality.CamRip,
            Regex("\\bcam\\b") to SearchQuality.Cam,
            Regex("\\b(web[- ]?dl|webrip|webdl)\\b") to SearchQuality.WebRip,
            Regex("\\b(bluray|blu[- ]?ray|bdrip)\\b") to SearchQuality.BlueRay,
            Regex("\\b(4k|2160p|uhd|ds4k)\\b") to SearchQuality.FourK,
            Regex("\\b(1440p|qhd)\\b") to SearchQuality.HD,
            Regex("\\b(1080p|fullhd)\\b") to SearchQuality.HD,
            Regex("\\b720p\\b") to SearchQuality.SD,
            Regex("\\b(hdrip|hdtv)\\b") to SearchQuality.HD,
            Regex("\\bdvd\\b") to SearchQuality.DVD,
            Regex("\\bhq\\b") to SearchQuality.HQ,
            Regex("\\brip\\b") to SearchQuality.CamRip
        )
        return patterns.firstNotNullOfOrNull { (regex, quality) -> quality.takeIf { regex.containsMatchIn(u) } }
    }
}
