package com.admknight.cuevana

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class CuevanaProvider : MainAPI() {
    override var mainUrl = "https://cuevana3.ch"
    override var name = "Cuevana"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        
        val series = runCatching {
            app.get("$mainUrl/serie", timeout = 120).document.select("section.home-series li")
                .map {
                    val title = it.selectFirst("h2.Title")?.text() ?: ""
                    val poster = it.selectFirst("img.lazy")?.attr("data-src")?.replaceFirst("//", "https://")
                    val url = it.selectFirst("a")?.attr("href") ?: ""
                    newTvSeriesSearchResponse(title, url, TvType.TvSeries) { 
                        this.posterUrl = poster 
                    }
                }
        }.getOrNull() ?: emptyList()
        items.add(HomePageList("Series", series))

        val urls = listOf(
            Pair("$mainUrl/peliculas", "Recientemente actualizadas"),
            Pair("$mainUrl/estrenos", "Estrenos"),
        )
        
        urls.forEach { (url, name) ->
            runCatching {
                val soup = app.get(url).document
                val home = soup.select("section li.xxx.TPostMv").map {
                    val title = it.selectFirst("h2.Title")?.text() ?: ""
                    val link = it.selectFirst("a")?.attr("href") ?: ""
                    val poster = it.selectFirst("img.lazy")?.attr("data-src")?.replaceFirst("//", "https://")
                    newMovieSearchResponse(title, link, if (link.contains("/pelicula/")) TvType.Movie else TvType.TvSeries) {
                        this.posterUrl = poster
                    }
                }
                items.add(HomePageList(name, home))
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search.html?keyword=${query}"
        val document = app.get(url).document

        return document.select("li.xxx.TPostMv").map {
            val title = it.selectFirst("h2.Title")?.text() ?: ""
            val href = it.selectFirst("a")?.attr("href")?.replaceFirst("/", "$mainUrl/") ?: ""
            val image = it.selectFirst("img.lazy")?.attr("data-src")?.replaceFirst("//", "https://")
            val isSerie = href.contains("/serie/")

            newMovieSearchResponse(title, href, if (isSerie) TvType.TvSeries else TvType.Movie) {
                this.posterUrl = image
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document
        val title = soup.selectFirst("h1.Title")?.text() ?: ""
        val description = soup.selectFirst(".Description p")?.text()?.trim()
        val poster = soup.selectFirst(".movtv-info div.Image img")?.attr("data-src")?.replace(Regex("\\/p\\/w\\d+.*\\/"), "/p/original/")
        val backgroundPoster = soup.selectFirst("img.lazy")?.attr("data-src")?.replaceFirst("//", "https://")
        
        val footerMeta = soup.selectFirst("footer p.meta")?.text() ?: ""
        val year = Regex("(\\d{4})").find(footerMeta)?.groupValues?.get(1)?.toIntOrNull()

        val episodes = soup.select(".all-episodes li.TPostMv article").map { li ->
            val href = li.select("a").attr("href").replaceFirst("^/".toRegex(), "$mainUrl/")
            val epThumb = li.selectFirst("div.Image img")?.attr("data-src") ?: li.selectFirst("img.lazy")?.attr("data-src")?.replace(Regex("\\/w\\d+\\/"), "/w780/")
            
            val seasonId = li.selectFirst("span.Year")?.text() ?: ""
            val parts = seasonId.split("x").mapNotNull { it.toIntOrNull() }
            val season = parts.getOrNull(0)
            val episodeNum = parts.getOrNull(1)
            
            newEpisode(href) {
                this.season = season
                this.episode = episodeNum
                this.posterUrl = fixUrl(epThumb ?: "")
            }
        }
        
        val tags = soup.select("ul.InfoList li.AAIco-adjust:contains(Genero) a").map { it.text() }
        val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries
        
        val recElement = if (tvType == TvType.TvSeries) "main section div.series_listado.series div.xxx" else "main section ul.MovieList li"
        val recommendations = soup.select(recElement).mapNotNull { element ->
            val recTitle = element.select("h2.Title").text().ifEmpty { return@mapNotNull null }
            val image = element.select("figure img").attr("data-src")
            val recUrl = fixUrl(element.select("a").attr("href"))
            newMovieSearchResponse(recTitle, recUrl, if (recUrl.contains("/movie/")) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = image
            }
        }
        val trailer = soup.selectFirst("div.TPlayer.embed_div div[id=OptY] iframe")?.attr("data-src") ?: ""

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.plot = description
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.backgroundPosterUrl = backgroundPoster
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
                if (trailer.isNotBlank()) addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("li.open_submenu li.clili").forEach {
            val iframe = fixUrl(it.attr("data-video").replaceFirst("^//".toRegex(), "https://"))
            loadExtractor(iframe, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
