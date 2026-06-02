package com.admknight.seriesflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class SeriesflixProvider : MainAPI() {
    override var mainUrl = "https://seriesflix.fit"
    override var name = "Seriesflix"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/series-online/", "Series"),
            Pair("$mainUrl/genero/accion/", "Acción"),
            Pair("$mainUrl/genero/ciencia-ficcion/", "Ciencia ficción"),
        )
        urls.forEach { (url, name) ->
            val soup = app.get(url).document
            val home = soup.select("article.TPost.B").map {
                val title = it.selectFirst("h2.title")?.text() ?: ""
                val link = it.selectFirst("a")?.attr("href") ?: ""
                val img = it.selectFirst("img")?.attr("data-src")?.replace("//tmdbcdn2.online", "https://tmdbcdn2.online")?.replace(".webp", ".jpg") ?: ""
                newMovieSearchResponse(title, link, TvType.TvSeries) {
                    this.posterUrl = img
                }
            }
            if (home.isNotEmpty()) items.add(HomePageList(name, home))
        }
        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("article.TPost.B").map {
            val href = it.selectFirst("a")?.attr("href") ?: ""
            val poster = it.selectFirst("figure img")?.attr("src")
            val name = it.selectFirst("h2.title")?.text() ?: ""
            val isMovie = href.contains("/movies/")
            
            newMovieSearchResponse(name, href, if (isMovie) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val type = if (url.contains("/movies/")) TvType.Movie else TvType.TvSeries
        val document = app.get(url).document

        val title = document.selectFirst("h1.Title")?.text() ?: ""
        val descipt = document.selectFirst("div.Description > p")?.text()?.replace(Regex("(Recuerda.*Seriesflix.)"), "") ?: ""
        val scoreValue = Score.from10(document.selectFirst("div.Vote > div.post-ratings > span")?.text())
        val year = document.selectFirst("span.Date")?.text()
        val duration = document.selectFirst("span.Time")?.text()
        
        val posterStr = document.selectFirst("head")?.toString() ?: ""
        val poster = Regex("(\"og:image\" content=\"https://seriesflix.video/wp-content/uploads/(\\d+)/(\\d+)/?.*.jpg)").find(posterStr)?.value?.replace("\"og:image\" content=\"", "") 
            ?: document.selectFirst(".TPostBg")?.attr("src")

        if (type == TvType.TvSeries) {
            val seasonList = document.select("main > section.SeasonBx > div > div.Title > a").mapNotNull { element ->
                val sNum = element.selectFirst("> span")?.text()?.toIntOrNull()
                val sHref = element.attr("href")
                if (sNum != null && sNum > 0 && sHref.isNotBlank()) Pair(sNum, fixUrl(sHref)) else null
            }
            if (seasonList.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodes = ArrayList<Episode>()
            seasonList.forEach { (sNum, sUrl) ->
                val sDoc = app.get(sUrl).document
                sDoc.select("table > tbody > tr").forEach { li ->
                    val epNum = li.selectFirst("> td > span.Num")?.text()?.toIntOrNull()
                    val epThumb = li.selectFirst("img")?.attr("src")
                    val aName = li.selectFirst("> td.MvTbTtl > a")
                    if (aName != null) {
                        episodes.add(newEpisode(aName.attr("href")) {
                            this.name = aName.text()
                            this.season = sNum
                            this.episode = epNum
                            this.posterUrl = fixUrlNull(epThumb)
                        })
                    }
                }
            }
            return newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.year = year?.toIntOrNull()
                this.plot = descipt
                this.score = scoreValue
            }
        } else {
            return newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = fixUrlNull(poster)
                this.year = year?.toIntOrNull()
                this.plot = descipt
                this.score = scoreValue
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
        app.get(data).document.select("li div.Button.sgty").forEach {
            val encoded = it.attr("data-url")
            val decoded = base64Decode(encoded)
            loadExtractor(decoded, data, subtitleCallback, callback)
        }
        return true
    }
}
