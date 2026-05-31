package com.admknight.allmoviesforyou

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class AllMoviesForYouProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("series") -> TvType.TvSeries
                t.contains("movies") -> TvType.Movie
                else -> TvType.Movie
            }
        }
    }

    // Fetching movies will not work if this link is outdated.
    override var mainUrl = "https://allmoviesforyou.net"
    override var name = "AllMoviesForYou"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        val urls = listOf(
            Pair("Movies", "section[data-id=movies] article.TPost.B"),
            Pair("TV Series", "section[data-id=series] article.TPost.B"),
        )
        for ((name, element) in urls) {
            try {
                val home = soup.select(element).map {
                    val title = it.selectFirst("h2.title")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    newTvSeriesSearchResponse(title, link, TvType.Movie) {
                        this.posterUrl = fixUrl(it.selectFirst("figure img")!!.attr("data-src"))
                    }
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document

        val items = document.select("ul.MovieList > li > article > a")
        return items.map { item ->
            val href = item.attr("href")
            val title = item.selectFirst("> h2.Title")!!.text()
            val img = fixUrl(item.selectFirst("> div.Image > figure > img")!!.attr("data-src"))
            val type = getType(href)
            if (type == TvType.Movie) {
                newMovieSearchResponse(title, href, type) {
                    this.posterUrl = img
                }
            } else {
                newTvSeriesSearchResponse(title, href, type) {
                    this.posterUrl = img
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val type = getType(url)

        val document = app.get(url).document

        val title = document.selectFirst("h1.Title")!!.text()
        val descipt = document.selectFirst("div.Description > p")!!.text()
        val year = document.selectFirst("span.Date")?.text()
        val backgroundPoster =
            fixUrlNull(document.selectFirst("div.Image > figure > img")?.attr("src"))
        var tags: List<String>? = null
        var cast: List<String>? = null
        document.select("div.Description > p").forEach { element ->
            val newtype = element.select("span")!!.text() ?: return@forEach
            when {
                newtype.contains("Genre") -> {
                    tags = element.select("a").mapNotNull { it.text() }
                }
                newtype.contains("Cast") -> {
                    cast = element.select("a").mapNotNull { it.text() }
                }
            }
        }
        
        if (type == TvType.TvSeries) {
            val list = ArrayList<Pair<Int, String>>()

            document.select("main > section.SeasonBx > div > div.Title > a").forEach { element ->
                val season = element.selectFirst("> span")?.text()?.toIntOrNull()
                val href = element.attr("href")
                if (season != null && season > 0 && !href.isNullOrBlank()) {
                    list.add(Pair(season, fixUrl(href)))
                }
            }
            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()

            for (season in list) {
                val seasonResponse = app.get(season.second).text
                val seasonDocument = Jsoup.parse(seasonResponse)
                val episodes = seasonDocument.select("table > tbody > tr")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val epNum = episode.selectFirst("> td > span.Num")?.text()?.toIntOrNull()
                        val poster = episode.selectFirst("> td.MvTbImg > a > img")?.attr("data-src")
                        val aName = episode.selectFirst("> td.MvTbTtl > a")
                        val name = aName!!.text()
                        val href = aName.attr("href")
                        val date = episode.selectFirst("> td.MvTbTtl > span")?.text()

                        episodeList.add(
                            newEpisode(href) {
                                this.name = name
                                this.season = season.first
                                this.episode = epNum
                                this.posterUrl = fixUrlNull(poster)
                                addDate(date)
                            }
                        )
                    }
                }
            }
            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodeList
            ) {
                posterUrl = backgroundPoster
                this.year = year?.toIntOrNull()
                this.plot = descipt
                this.tags = tags
                this.score = Score.from10(document.selectFirst("div.Vote > div.post-ratings > span")?.text())
                addActors(cast)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                type,
                fixUrl(url)
            ) {
                posterUrl = backgroundPoster
                this.year = year?.toIntOrNull()
                this.plot = descipt
                this.tags = tags
                this.score = Score.from10(document.selectFirst("div.Vote > div.post-ratings > span")?.text())
                addActors(cast)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframe = doc.select("body iframe").map { fixUrl(it.attr("src")) }
        iframe.forEach { id ->
            if (id.contains("trembed")) {
                val soup = app.get(id).document
                soup.select("body iframe").map {
                    val link = fixUrl(it.attr("src").replace("streamhub.to/d/", "streamhub.to/e/"))
                    loadExtractor(link, data, subtitleCallback, callback)
                }
            } else loadExtractor(id, data, subtitleCallback, callback)
        }
        return true
    }
}
