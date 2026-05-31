package com.admknight.cinecalidad

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class CinecalidadProvider : MainAPI() {
    override var mainUrl = "https://www.cinecalidad.ec"
    override var name = "Cinecalidad"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded //Due to evoload sometimes not loading

    override val mainPage = mainPageOf(
        "$mainUrl/ver-serie/page/" to "Series",
        "$mainUrl/page/" to "Peliculas",
        "$mainUrl/genero-de-la-pelicula/peliculas-en-calidad-4k/page/" to "4K UHD",
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val url = request.data + page

        val soup = app.get(url).document
        val home = soup.select(".item.movies").map {
            val title = it.selectFirst("div.in_title")!!.text()
            val link = it.selectFirst("a")!!.attr("href")
            newTvSeriesSearchResponse(
                title,
                link,
                if (link.contains("/ver-pelicula/")) TvType.Movie else TvType.TvSeries
            ) {
                this.posterUrl = it.selectFirst(".poster.custom img")!!.attr("data-src")
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select("article.item").map {
            val title = it.selectFirst("div.in_title")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("img.lazy")!!.attr("data-src")
            val isMovie = href.contains("/ver-pelicula/")

            if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = image
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = image
                }
            }
        }
    }


    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document

        val title = soup.selectFirst(".single_left h1")!!.text()
        val description = soup.selectFirst("div.single_left table tbody tr td p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".alignnone")!!.attr("data-src")
        val episodes = soup.select("div.se-c div.se-a ul.episodios li").map { li ->
            val href = li.selectFirst("a")!!.attr("href")
            val epThumb = li.selectFirst("img.lazy")!!.attr("data-src")
            val name = li.selectFirst(".episodiotitle a")!!.text()
            val seasonid =
                li.selectFirst(".numerando")!!.text().replace(Regex("(S|E)"), "").let { str ->
                    str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
                }
            val isValid = seasonid.size == 2
            val episode = if (isValid) seasonid.getOrNull(1) else null
            val season = if (isValid) seasonid.getOrNull(0) else null
            newEpisode(href) {
                this.name = name
                this.season = season
                this.episode = episode
                this.posterUrl = if (epThumb.contains("svg")) null else epThumb
            }
        }
        val tvType = if (url.contains("/ver-pelicula/")) TvType.Movie else TvType.TvSeries
        return when (tvType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                }
            }
            TvType.Movie -> {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = description
                }
            }
            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
         app.get(data).document.select(".linklist ul li").map {
             val url = it.select("li").attr("data-option")
             loadExtractor(url, mainUrl, subtitleCallback, callback)
         }


        return true
    }
}
