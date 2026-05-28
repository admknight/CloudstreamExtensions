package com.admknight.archivemovies

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class ArchiveMoviesProvider : MainAPI() {

    override var mainUrl = "https://archive.org"
    override var name = "Archive Movies"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"

    override suspend fun search(query: String): List<SearchResponse> {

        val url =
            "$mainUrl/search?query=${query.replace(" ", "+")}%20AND%20mediatype%3Amovies"

        val document = app.get(url).document

        return document.select(".item-ia").mapNotNull {

            val title =
                it.selectFirst(".ttl")?.text()?.trim()
                    ?: return@mapNotNull null

            val href =
                it.selectFirst("a")?.attr("href")
                    ?: return@mapNotNull null

            val poster =
                it.selectFirst("img")?.attr("src")

            newMovieSearchResponse(
                title,
                fixUrl(href),
                TvType.Movie
            ) {
                this.posterUrl = poster?.let { p -> fixUrl(p) }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document

        val title =
            document.selectFirst("h1")?.text()?.trim()
                ?: return null

        val poster =
            document.selectFirst("meta[property=og:image]")
                ?.attr("content")

        val description =
            document.selectFirst("meta[name=description]")
                ?.attr("content")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val video =
            document.select("a[href$=.mp4]")
                .map { it.attr("href") }
                .firstOrNull()

        if (video != null) {

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "MP4",
                    url = fixUrl(video),
                ) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                }
            )

            return true
        }

        return false
    }
}
