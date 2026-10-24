package recloudstream

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
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
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

class World4uFreeProvider : MainAPI() {
    override var mainUrl = "https://worldfree4u.blog"
    override var name = "World4uFree"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Latest",
        "$mainUrl/category/bollywood-movies/page/" to "Bollywood",
        "$mainUrl/category/hollywood-movies/page/" to "Hollywood",
        "$mainUrl/category/hindi-web-series/page/" to "Hindi Web Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val results = parseCards(document)
        return newHomePageResponse(
            listOf(HomePageList(request.name, results, isHorizontalImages = false)),
            hasNext = results.isNotEmpty(),
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val prefix = if (page <= 1) "" else "page/$page/"
        val document = app.get("$mainUrl/$prefix?s=${query.encodeUri()}").document
        return parseCards(document).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: document.selectFirst("h1.entry-title, h1")?.text()?.trim()
            ?: url.substringAfterLast('/').replace('-', ' ')

        val title = rawTitle
            .replace(Regex("""(?i)^download\s+"""), "")
            .replace(Regex("""(?i)\s*[|–-]\s*world(?:4u|free4u).*?$"""), "")
            .trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?: document.selectFirst(".entry-content img, article img, main img")?.imageUrl()
        )

        val plot = document.select(
            ".entry-content p, article p, main p"
        ).map { it.text().trim() }.firstOrNull { text ->
            text.length >= 70 &&
                (
                    text.contains("plot", true) ||
                    text.contains("story", true) ||
                    text.contains("synopsis", true)
                )
        } ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val year = Regex("""\b(19|20)\d{2}\b""")
            .find(title)?.value?.toIntOrNull()

        val tags = document.select("a[rel=category], a[href*='/category/']")
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(data).document

        val links = document.select("iframe[src], a[href]")
            .mapNotNull { element ->
                val raw = if (element.hasAttr("src")) element.attr("src") else element.attr("href")
                raw.trim().takeIf { it.isNotBlank() }
            }
            .map { fixUrl(it) }
            .filter { isSupportedPublicEmbed(it) }
            .distinct()

        var emittedLinks = 0
        val trackedCallback: (ExtractorLink) -> Unit = { extracted ->
            emittedLinks += 1
            callback(extracted)
        }

        links.forEach { link ->
            loadExtractor(link, data, subtitleCallback, trackedCallback)
        }

        return emittedLinks > 0
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<SearchResponse>()

        val cards = document.select(
            "article, .post, .post-item, .recent-posts > li, ul.recent-posts > li, main li"
        )

        cards.forEach { card ->
            val parsed = card.toSearchResult() ?: return@forEach
            if (seen.add(parsed.url)) results.add(parsed)
        }

        if (results.isEmpty()) {
            document.select("a[href]").forEach { anchor ->
                val parsed = anchor.toSearchResult() ?: return@forEach
                if (seen.add(parsed.url)) results.add(parsed)
            }
        }

        return results
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (tagName() == "a") {
            this
        } else {
            selectFirst("h1 a[href], h2 a[href], h3 a[href], .title a[href], a[title][href], a[href]")
        } ?: return null

        val hrefRaw = anchor.attr("href").trim()
        if (hrefRaw.isBlank()) return null
        val href = fixUrl(hrefRaw)

        val uri = runCatching { URI(href) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        if (host != "worldfree4u.blog" && !host.endsWith(".worldfree4u.blog")) return null

        val path = uri.path.orEmpty()
        if (
            path == "/" ||
            path.contains("/category/", true) ||
            path.contains("/tag/", true) ||
            path.contains("/page/", true) ||
            path.contains("/privacy", true) ||
            path.contains("/disclaimer", true) ||
            path.contains("/contact", true) ||
            path.contains("/dmca", true)
        ) return null

        val card = if (tagName() == "a") closest("article") ?: parent() else this
        val image = card?.selectFirst("img") ?: anchor.selectFirst("img")

        val title = image?.attr("alt")?.trim()?.takeIf { it.length >= 4 }
            ?: anchor.attr("title").trim().takeIf { it.length >= 4 }
            ?: card?.selectFirst("h1, h2, h3, .title")?.text()?.trim()?.takeIf { it.length >= 4 }
            ?: anchor.text().trim().takeIf { it.length >= 4 }
            ?: return null

        val cleanedTitle = title
            .replace(Regex("""(?i)^download\s+"""), "")
            .trim()

        val poster = image?.imageUrl()
        val qualityName = Regex("""(?i)\b(2160p|1080p|720p|480p|360p|4k|hdtc|hdcam)\b""")
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

    private fun isSupportedPublicEmbed(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        return host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "vimeo.com" ||
            host.endsWith(".vimeo.com") ||
            host == "archive.org" ||
            host.endsWith(".archive.org")
    }
}
