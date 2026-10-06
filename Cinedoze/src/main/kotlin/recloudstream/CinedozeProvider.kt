package recloudstream

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

class CinedozeProvider : MainAPI() {
    override var mainUrl = "https://cinedoze.tv"
    override var name = "Cinedoze"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        mainUrl to "Latest",
        "$mainUrl/genre/bollywood-movies/" to "Bollywood",
        "$mainUrl/genre/hollywood-movies/" to "Hollywood",
        "$mainUrl/genre/tv-series-shows/" to "TV Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(pagedUrl(request.data, page)).document
        val results = parseCards(document)
        return newHomePageResponse(
            listOf(HomePageList(request.name, results, isHorizontalImages = false)),
            hasNext = results.isNotEmpty(),
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val base = "$mainUrl/search/${query.encodeUri()}/"
        val document = app.get(pagedUrl(base, page)).document
        return parseCards(document).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst(".sheader .data h1, .data h1, h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: url.substringAfterLast('/').replace('-', ' ')

        val poster = fixUrlNull(
            document.selectFirst(".poster img, .sheader .poster img")?.imageUrl()
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("article img, main img")?.imageUrl()
        )

        val plot = document.selectFirst(".wp-content, .description, .sbox .wp-content, .contenido")
            ?.text()?.trim()?.takeIf { it.length >= 60 }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val year = Regex("""\b(19|20)\d{2}\b""")
            .find(document.selectFirst(".extra, .date, .data")?.text().orEmpty() + " " + title)
            ?.value?.toIntOrNull()

        val tags = document.select("a[href*='/genre/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val recommendations = parseCards(document)
            .filterNot { it.url.trimEnd('/') == url.trimEnd('/') }
            .distinctBy { it.url }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    private fun pagedUrl(base: String, page: Int): String {
        if (page <= 1) return base
        return "${base.trimEnd('/')}/page/$page/"
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val seen = mutableSetOf<String>()
        val output = mutableListOf<SearchResponse>()

        document.select("article").forEach { article ->
            val result = article.toSearchResult() ?: return@forEach
            if (seen.add(result.url)) output.add(result)
        }

        if (output.isEmpty()) {
            document.select("a[href*='/movies/'], a[href*='/tvshows/']").forEach { anchor ->
                val result = anchor.toSearchResult() ?: return@forEach
                if (seen.add(result.url)) output.add(result)
            }
        }

        return output
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (tagName() == "a") this else
            selectFirst("a[href*='/movies/'], a[href*='/tvshows/']") ?: return null

        val hrefRaw = anchor.attr("href").trim()
        if (hrefRaw.isBlank()) return null
        val href = fixUrl(hrefRaw)

        val uri = runCatching { URI(href) }.getOrNull() ?: return null
        val path = uri.path.orEmpty()
        if (!path.contains("/movies/", true) && !path.contains("/tvshows/", true)) return null

        val card = if (tagName() == "a") closest("article") ?: parent() else this
        val title = card?.selectFirst(".title, h3, h2")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: anchor.attr("title").trim().takeIf { it.isNotBlank() }
            ?: anchor.text().trim().takeIf { it.isNotBlank() }
            ?: return null

        val image = card?.selectFirst("img") ?: anchor.selectFirst("img")
        val poster = image?.imageUrl()
        val qualityName = Regex("""(?i)\b(2160p|1080p|720p|480p|360p|4k|web-dl|hdrip)\b""")
            .find(card?.text().orEmpty() + " " + title)?.value

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(poster)
            this.quality = getQualityFromString(qualityName)
        }
    }

    private fun Element.imageUrl(): String? {
        return attr("data-src").takeIf { it.isNotBlank() }
            ?: attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: attr("data-original").takeIf { it.isNotBlank() }
            ?: attr("src").takeIf { it.isNotBlank() }
    }
}
