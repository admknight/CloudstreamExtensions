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

class Full4MoviesProvider : MainAPI() {
    override var mainUrl = "https://fullmoviesmx.com"
    override var name = "Full4Movies"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Latest",
        "$mainUrl/tamil-movies/page/" to "Tamil Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page + "/").document
        val results = parseCards(document)
        return newHomePageResponse(
            listOf(HomePageList(request.name, results, isHorizontalImages = false)),
            hasNext = results.isNotEmpty(),
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val suffix = if (page <= 1) "" else "page/$page/"
        val document = app.get("$mainUrl/$suffix?s=${query.encodeUri()}").document
        return parseCards(document).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val rawTitle = document.selectFirst("h1.title, h1.entry-title, h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: url.substringAfterLast('/').substringBefore(".html").replace('-', ' ')

        val title = rawTitle
            .replace(Regex("""(?i)^download\s+"""), "")
            .replace(Regex("""(?i)\s*[|–-]\s*Full4Movies.*$"""), "")
            .trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?: document.selectFirst(".entry-content img, article img, main img")?.imageUrl()
        )

        val year = Regex("""\b(19|20)\d{2}\b""").find(title)?.value?.toIntOrNull()
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
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
            this.recommendations = recommendations
        }
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<SearchResponse>()

        document.select("div.article-content-col, article, .post, .post-item, main article").forEach { card ->
            val parsed = card.toSearchResult() ?: return@forEach
            if (seen.add(parsed.url)) results.add(parsed)
        }

        if (results.isEmpty()) {
            document.select("a[href$='.html']").forEach { anchor ->
                val parsed = anchor.toSearchResult() ?: return@forEach
                if (seen.add(parsed.url)) results.add(parsed)
            }
        }

        return results
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (tagName() == "a") this else
            selectFirst("h1 a[href], h2 a[href], h3 a[href], .title a[href], a[title][href], a[href$='.html']")
            ?: return null

        val hrefRaw = anchor.attr("href").trim()
        if (hrefRaw.isBlank()) return null
        val href = fixUrl(hrefRaw)

        val uri = runCatching { URI(href) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        if (host != "fullmoviesmx.com" && !host.endsWith(".fullmoviesmx.com")) return null
        if (!uri.path.orEmpty().endsWith(".html", ignoreCase = true)) return null

        val card = if (tagName() == "a") closest("article") ?: parent() else this
        val image = card?.selectFirst("img") ?: anchor.selectFirst("img")
        val title = anchor.attr("title").trim().takeIf { it.length >= 4 }
            ?: image?.attr("alt")?.trim()?.takeIf { it.length >= 4 }
            ?: card?.selectFirst("h1, h2, h3, .title")?.text()?.trim()?.takeIf { it.length >= 4 }
            ?: anchor.text().trim().takeIf { it.length >= 4 }
            ?: return null

        val cleanedTitle = title.replace(Regex("""(?i)^download\s+"""), "").trim()
        val poster = image?.imageUrl()
        val qualityName = Regex("""(?i)\b(2160p|1080p|720p|480p|360p|4k|hdtc|hdcam|web-dl|hdrip)\b""")
            .find(card?.text().orEmpty() + " " + cleanedTitle)?.value

        return newMovieSearchResponse(cleanedTitle, href, TvType.Movie) {
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
