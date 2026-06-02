package com.admknight.sololatino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class SoloLatinoProvider : MainAPI() {
    override var mainUrl = "https://sololatino.net"
    override var name = "SoloLatino"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("Peliculas", "$mainUrl/peliculas"),
            Pair("Series", "$mainUrl/series"),
            Pair("Animes", "$mainUrl/animes"),
            Pair("Cartoons", "$mainUrl/genre_series/toons"),
        )

        urls.forEach { (name, url) ->
            val doc = app.get(url).document
            val home = doc.select("div.items article.item").mapNotNull {
                val title = it.selectFirst("a div.data h3")?.text() ?: ""
                val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = it.selectFirst("div.poster img.lazyload")?.attr("data-srcset")
                newMovieSearchResponse(title, link, TvType.Movie) { 
                    this.posterUrl = img 
                }
            }
            if (home.isNotEmpty()) items.add(HomePageList(name, home))
        }
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("div.items article.item").mapNotNull {
            val title = it.selectFirst("a div.data h3")?.text() ?: ""
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val img = it.selectFirst("div.poster img.lazyload")?.attr("data-srcset")
            newMovieSearchResponse(title, link, TvType.Movie) { 
                this.posterUrl = img 
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val isMovie = url.contains("peliculas")
        val title = doc.selectFirst("div.data h1")?.text() ?: ""
        val poster = doc.selectFirst("div.poster img")?.attr("src") ?: ""
        val description = doc.selectFirst("div.wp-content")?.text() ?: ""
        val tags = doc.select("div.sgeneros a").map { it.text() }
        
        val episodes = if (!isMovie) {
            doc.select("div#seasons div.se-c").flatMap { season ->
                season.select("ul.episodios li").mapNotNull {
                    val epUrl = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
                    val epTitle = it.selectFirst("div.episodiotitle div.epst")?.text()
                    val numText = it.selectFirst("div.episodiotitle div.numerando")?.text() ?: ""
                    val parts = numText.split("-").mapNotNull { it.trim().toIntOrNull() }
                    val epThumb = it.selectFirst("div.imagen img")?.attr("src")
                    
                    newEpisode(epUrl) {
                        this.name = epTitle
                        this.season = parts.getOrNull(0)
                        this.episode = parts.getOrNull(1)
                        this.posterUrl = epThumb
                    }
                }
            }
        } else emptyList()

        return if (!isMovie) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val regex = """(go_to_player|go_to_playerVast)\('(.*?)'""".toRegex()
        app.get(data).document.selectFirst("iframe")?.attr("src")?.let { frameUrl ->
            val html = app.get(frameUrl).document.html()
            regex.findAll(html).forEach { match ->
                val link = match.groupValues[2]
                loadExtractor(link, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
