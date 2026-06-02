package com.admknight.pelispedia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PelispediaProvider : MainAPI() {
    override var mainUrl = "https://pelispedia.is"
    override var name = "Pelispedia"
    override var lang = "es"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("Películas", "$mainUrl/cartelera-peliculas/"),
            Pair("Series", "$mainUrl/cartelera-series/"),
        )
        urls.forEach { (name, url) ->
            val doc = app.get(url).document
            val home = doc.select("section.movies article").mapNotNull {
                val title = it.selectFirst("h2.entry-title")?.text() ?: ""
                val img = it.selectFirst("img")?.attr("src") ?: ""
                val link = it.selectFirst("a.lnk-blk")?.attr("href") ?: return@mapNotNull null
                newMovieSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = fixUrl(img)
                }
            }
            items.add(HomePageList(name, home))
        }
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("section.movies article").mapNotNull {
            val title = it.selectFirst("h2.entry-title")?.text() ?: ""
            val img = it.selectFirst("img")?.attr("src") ?: ""
            val link = it.selectFirst("a.lnk-blk")?.attr("href") ?: return@mapNotNull null
            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = fixUrl(img)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val tvType = if (url.contains("pelicula")) TvType.Movie else TvType.TvSeries
        val poster = doc.selectFirst(".alg-ss img")?.attr("src")?.replace(Regex("\\/p\\/w\\d+.*\\/"), "/p/original/") ?: ""
        val backImage = doc.selectFirst(".bghd img")?.attr("src")?.replace(Regex("\\/p\\/w\\d+.*\\/"), "/p/original/") ?: poster
        val title = doc.selectFirst("h1.entry-title")?.text() ?: ""
        val plot = doc.selectFirst(".description > p:nth-child(2)")?.text() ?: doc.selectFirst(".description > p")?.text()
        val tags = doc.select("span.genres a").map { it.text() }
        val yearVal = doc.selectFirst("span.year.fa-calendar.far")?.text()?.toIntOrNull()
        val duration = doc.selectFirst("span.duration.fa-clock.far")?.text()
        
        val epi = ArrayList<Episode>()
        doc.select("div.choose-season li a").forEach {
            val seriesId = it.attr("data-post")
            val dataSeason = it.attr("data-season")
            val seasonDoc = app.post("$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf("action" to "action_select_season", "season" to dataSeason, "post" to seriesId)
            ).document
            
            seasonDoc.select("li article.episodes").forEach { li ->
                val href = li.selectFirst("a.lnk-blk")?.attr("href") ?: return@forEach
                val parts = href.substringAfter("temporada-").split("-capitulo-")
                val sNum = parts.getOrNull(0)?.toIntOrNull()
                val eNum = parts.getOrNull(1)?.toIntOrNull()
                epi.add(newEpisode(href) {
                    this.season = sNum
                    this.episode = eNum
                })
            }
        }

        val recs = doc.select("article.movies").mapNotNull { rec ->
            val rTitle = rec.selectFirst(".entry-title")?.text() ?: ""
            val rImg = rec.selectFirst("img")?.attr("src") ?: ""
            val rLink = rec.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            newMovieSearchResponse(rTitle, rLink, TvType.TvSeries) {
                this.posterUrl = fixUrl(rImg)
            }
        }

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, epi) {
                this.posterUrl = fixUrl(poster)
                this.backgroundPosterUrl = fixUrl(backImage)
                this.plot = plot
                this.tags = tags
                this.year = yearVal
                this.recommendations = recs
                addDuration(duration)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
                this.backgroundPosterUrl = fixUrl(backImage)
                this.plot = plot
                this.tags = tags
                this.year = yearVal
                this.recommendations = recs
                addDuration(duration)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select(".player iframe").forEach {
            val tEmbed = it.attr("data-src")
            val tDoc = app.get(tEmbed).document
            val link = tDoc.selectFirst("div.Video iframe")?.attr("src")
            if (link != null) loadExtractor(link, data, subtitleCallback, callback)
        }
        return true
    }
}
