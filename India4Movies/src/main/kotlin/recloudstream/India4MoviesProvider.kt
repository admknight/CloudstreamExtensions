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

class India4MoviesProvider : MainAPI() {
    override var mainUrl = "https://go3.india4movies.net"
    override var name = "India4Movies"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        mainUrl to "Latest",
        "$mainUrl/category/new-releases/" to "New Releases",
        "$mainUrl/category/hollywood-movies/" to "Hollywood",
        "$mainUrl/category/bollywood-movies/" to "Bollywood",
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
        val suffix = if (page <= 1) "" else "&paged=$page"
        val document = app.get("$mainUrl/?s=${query.encodeUri()}$suffix").document
        return parseCards(document).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title, h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: url.substringAfterLast('/').replace('-', ' ')

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?: document.selectFirst(".entry-content img, article img, main img")?.imageUrl()
        )

        val released = document.fieldValue("Released Date")
        val year = Regex("""\b(19|20)\d{2}\b""")
            .find(released.orEmpty() + " " + title)?.value?.toIntOrNull()

        val genres = document.fieldValue("All Genres")
            ?.split(Regex("""\s*,\s*"""))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val plot = document.fieldValue("Plot")
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.select(".entry-content p, article p, main p")
                .map { it.text().trim() }
                .firstOrNull { it.length >= 80 }

        val recommendations = parseCards(document)
            .filterNot { it.url.trimEnd('/') == url.trimEnd('/') }
            .distinctBy { it.url }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = genres
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

        document.select("article, .post, .type-post, main article").forEach { card ->
            val result = card.toSearchResult() ?: return@forEach
            if (seen.add(result.url)) output.add(result)
        }

        if (output.isEmpty()) {
            document.select("h2 a[href], h3 a[href]").forEach { anchor ->
                val result = anchor.toSearchResult() ?: return@forEach
                if (seen.add(result.url)) output.add(result)
            }
        }

        return output
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (tagName() == "a") this else
            selectFirst("h1 a[href], h2 a[href], h3 a[href], .entry-title a[href], a[title][href]")
            ?: return null

        val hrefRaw = anchor.attr("href").trim()
        if (hrefRaw.isBlank()) return null
        val href = fixUrl(hrefRaw)

        val uri = runCatching { URI(href) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        if (!host.endsWith(".india4movies.net") && host != "india4movies.net") return null

        val path = uri.path.orEmpty()
        if (
            path == "/" ||
            path.contains("/category/", true) ||
            path.contains("/movie_language/", true) ||
            path.contains("/tag/", true) ||
            path.contains("/page/", true)
        ) return null

        val card = if (tagName() == "a") closest("article") ?: parent() else this
        val image = card?.selectFirst("img") ?: anchor.selectFirst("img")
        val title = card?.selectFirst("h1, h2, h3, .entry-title")?.text()?.trim()?.takeIf { it.length >= 4 }
            ?: image?.attr("alt")?.trim()?.takeIf { it.length >= 4 }
            ?: anchor.attr("title").trim().takeIf { it.length >= 4 }
            ?: anchor.text().trim().takeIf { it.length >= 4 }
            ?: return null

        val poster = image?.imageUrl()
        val qualityName = Regex("""(?i)\b(2160p|1080p|720p|480p|360p|4k|hdtc|hdts|hdcam|web-hdrip|hdrip)\b""")
            .find(card?.text().orEmpty() + " " + title)?.value

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(poster)
            this.quality = getQualityFromString(qualityName)
        }
    }

    private fun Document.fieldValue(label: String): String? {
        val regex = Regex("""^\s*${Regex.escape(label)}\s*:\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)

        select("p, li, div, span").forEach { element ->
            val own = element.ownText().trim()
            val ownMatch = regex.find(own)
            if (ownMatch != null) {
                return ownMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
            }

            val combined = element.text().trim()
            if (combined.length <= 400) {
                val combinedMatch = regex.find(combined)
                if (combinedMatch != null) {
                    return combinedMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
                }
            }
        }

        return null
    }

    private fun Element.imageUrl(): String? {
        return attr("data-src").takeIf { it.isNotBlank() }
            ?: attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: attr("data-original").takeIf { it.isNotBlank() }
            ?: attr("src").takeIf { it.isNotBlank() }
    }
}
