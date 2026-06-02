package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AllMoviesForYouProvider : MainAPI() {
    override var mainUrl = "https://allmoviesforyou.net"
    override var name = "AllMoviesForYou"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(mainUrl).document
        val items = ArrayList<HomePageList>()
        val testa = listOf(
            Pair("Featured", "div#featured-titles article"),
            Pair("Movies", "div#movies-block article"),
            Pair("TV Shows", "div#tvshows-block article"),
        )
        testa.forEach { (name, selector) ->
            val results = document.select(selector).map {
                it.toSearchResult()
            }
            items.add(HomePageList(name, results))
        }
        return newHomePageResponse(items)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h3")?.text() ?: ""
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("img")?.attr("src")
        val isMovie = href.contains("/movies/")

        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.search-page article").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: ""
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val description = document.selectFirst("div.wp-content p")?.text()
        val isMovie = url.contains("/movies/")

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val episodes = document.select("ul.episodios li").map {
                val href = it.selectFirst("a")!!.attr("href")
                val name = it.selectFirst("div.episodiotitle a")?.text() ?: ""
                val epNum = it.selectFirst("div.numerando")?.text()?.substringAfter("-")?.trim()?.toIntOrNull()
                val seasonNum = it.selectFirst("div.numerando")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
                newEpisode(href) {
                    this.name = name
                    this.episode = epNum
                    this.season = seasonNum
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("ul#playeroptionsul li").forEach {
            val id = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")
            val response = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to id,
                    "nume" to nume,
                    "type" to type
                )
            ).parsedSafe<PlayerResponse>()
            response?.embed_url?.let { embed ->
                loadExtractor(embed, data, subtitleCallback, callback)
            }
        }
        return true
    }

    data class PlayerResponse(
        @JsonProperty("embed_url") val embed_url: String?,
        @JsonProperty("type") val type: String?
    )
}
