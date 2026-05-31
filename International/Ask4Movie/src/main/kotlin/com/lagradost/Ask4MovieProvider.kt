package com.admknight.ask4movie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class Ask4MovieProvider : MainAPI() {
    override var mainUrl = "https://ask4movie.mx"

    override var name = "Ask4Movie"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AnimeMovie)
    override val hasMainPage = true

    private fun Element.toSearchResponse(): MovieSearchResponse {
        val posterRegex = Regex("""url\((.*?)\)""")
        val poster = posterRegex.find(this.attr("style"))?.groupValues?.get(1)

        val a = this.select("a")
        val href = a.attr("href")
        val title = a.text().trim()

        val year = Regex("""\((\d{4})\)$""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    private fun Element.articleToSearchResponse(): MovieSearchResponse {
        val poster = this.select("img").attr("src")

        val a = this.select("a")
        val href = a.attr("href")
        val title = a.attr("title")

        val year = Regex("""\((\d{4})\)$""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("div.item").map {
            it.toSearchResponse()
        }
    }

    private fun getIframe(html: String): String? {
        val data = Regex("""<script src="data:text\/javascript;base64,([^"']*)""").findAll(html)
            .lastOrNull()?.groupValues?.getOrNull(1) ?: return null
        val decoded = base64Decode(data)
        val iframeUrlRegex = Regex("""dir['"],['"]([^"']*)""")

        val iframeEncoded = iframeUrlRegex.find(decoded)?.groupValues?.getOrNull(1) ?: return null
        val iframe = base64Decode(iframeEncoded)
        return Jsoup.parse(iframe).select("iframe").attr("src")
    }

    private suspend fun getEpisodes(iframe: String): List<Episode> {
        val playlistDoc = app.get(iframe).document

        val episodeRegex = Regex("""S(\d+).E(\d+)""")
        return playlistDoc.select("span.episode").mapNotNull { episode ->
            val partialUrl = episode.attr("data-url")
            val fullUrl = "https://${URI(iframe).rawAuthority}$partialUrl"
            val info = episodeRegex.find(episode.text())
            val seasonIndex = info?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episodeIndex = info?.groupValues?.getOrNull(2)?.toIntOrNull()
            newEpisode(fullUrl) {
                this.episode = episodeIndex
                this.season = seasonIndex
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val rows = document.select("div.row")

        val posterRegex = Regex("""url\((.*?)\)""")
        val mappedRows = rows.mapNotNull {
            var isHorizontal = true
            val items = it.select("div.slide-item").map { element ->
                val thumb = element.select("div.item-thumb")
                val poster = posterRegex.find(thumb.attr("style"))?.groupValues?.get(1)
                val href = thumb.select("a").attr("href")
                val title = element.select("div.video-short-intro a").text()
                val year =
                    Regex("""\((\d{4})\)$""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                    this.year = year
                }
            }.ifEmpty {
                isHorizontal = false
                it.select("div.channel-content.clearfix").map { searchElement ->
                    searchElement.articleToSearchResponse()
                }
            }

            val title = it.select("div.title").text()
            if (title.contains("porn", true)) return@mapNotNull null
            HomePageList(title, items, isHorizontal)
        }
        return newHomePageResponse(mappedRows)
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val seasons = document.select("div.item")
        val isSingleVideo = (seasons.isNullOrEmpty())
        val yearRegex = Regex("""\((\d{4})\)$""")

        if (isSingleVideo) {
            val description = document.select("div.custom.video-the-content").text().trim()
            val (title, year) = document.select("a.video-title").text().let {
                it.replace(yearRegex, "") to yearRegex.find(it)?.groupValues?.get(1)?.toIntOrNull()
            }
            val genres =
                document.selectFirst("div.categories.cactus-info")?.select("a")?.map { it.text() }

            val posterRegex = Regex("""contentUrl['"].*?(http[^"']*)""")
            val poster = posterRegex.find(response.text)?.groupValues?.get(1)
            val recommendations = document.select("div.cactus-sub-wrap article").mapNotNull {
                it.articleToSearchResponse()
            }

            val iframe = getIframe(response.text)
            return if (iframe?.contains("/p/") == true) {
                val episodes = getEpisodes(iframe)
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.recommendations = recommendations
                    this.tags = genres
                    this.plot = description
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, iframe) {
                    this.posterUrl = poster
                    this.tags = genres
                    this.plot = description
                    this.year = year
                    this.recommendations = recommendations
                }
            }
        } else {
            val recommendations = document.select("div.channel.clearfix").mapNotNull {
                it.articleToSearchResponse()
            }

            val descriptionDiv = document.select("div.channel-description")
            val description = descriptionDiv.select("p").firstOrNull()?.text()?.trim()

            val genres = descriptionDiv.select("span.genres").let {
                if (it.isNotEmpty()) it.text().split(",")
                else descriptionDiv.select("p")
                    .firstOrNull { element -> element.text().contains("Genre:") }
                    ?.text()?.substringAfter("Genre:")?.split(",")
            }

            val (title, year) = document.select("h3.channel-name").text().let {
                it.replace(yearRegex, "") to yearRegex.find(it)?.groupValues?.get(1)?.toIntOrNull()
            }

            val poster = document.select("div.channel-pic > img").attr("src")
            val mappedSeasons = seasons.map {
                val href = it.select("div.top-item > a").attr("href")
                val text = app.get(href).text
                val iframe = getIframe(text) ?: return@map emptyList()
                getEpisodes(iframe)
            }.flatten()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, mappedSeasons) {
                this.year = year
                this.posterUrl = poster
                this.recommendations = recommendations
                this.tags = genres
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
        loadExtractor(data, this.mainUrl, subtitleCallback, callback)
        return true
    }
}
