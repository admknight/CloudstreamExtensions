package com.Coflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class Coflix : MainAPI() {
    override var mainUrl = "https://coflix.tv"
    override var name = "Coflix"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/api/movies/popular?page=$page", "Popular Movies"),
            Pair("$mainUrl/api/series/popular?page=$page", "Popular Series"),
        )

        val items = ArrayList<HomePageList>()
        for (i in urls) {
            try {
                val response = app.get(i.first).parsed<Response>()
                val results = response.results.map {
                    newMovieSearchResponse(it.name, "$mainUrl/${it.path}/${it.slug}", TvType.Movie) {
                        this.posterUrl = "$mainUrl/storage/${it.ts}/${it.uuid}.jpg"
                    }
                }
                items.add(HomePageList(i.second, results))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/api/search?q=$query").parsed<Search>()
        return response.map {
            val type = if (it.postType == "movie") TvType.Movie else TvType.TvSeries
            newMovieSearchResponse(it.title, it.url, type) {
                this.posterUrl = it.image
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).document
        val title = response.selectFirst("h1")?.text() ?: ""
        val poster = response.selectFirst(".poster img")?.attr("src")
        val description = response.selectFirst(".description")?.text()
        val year = response.selectFirst(".year")?.text()?.toIntOrNull()
        val rating = response.selectFirst(".rating")?.text()?.toDoubleOrNull()?.times(10)?.toInt()

        val tvType = if (url.contains("/film/")) TvType.Movie else TvType.TvSeries

        if (tvType == TvType.TvSeries) {
            val id = url.substringAfterLast("-")
            val epRes = app.get("$mainUrl/api/series/$id/episodes").parsed<EpRes>()
            val episodes = epRes.episodes.map {
                newEpisode(it.id.toString()) {
                    this.name = it.title
                    this.episode = it.number.toIntOrNull()
                    this.season = it.season.toIntOrNull()
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = Score.from10(rating)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
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
        val response = app.get(data).document
        val players = response.select(".player-box iframe")
        players.forEach {
            val url = it.attr("src")
            loadExtractor(url, data, subtitleCallback, callback)
        }
        return true
    }
}
