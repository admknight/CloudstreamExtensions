package recloudstream

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
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

class CinevezProvider : MainAPI() {
    override var mainUrl = "https://cinevez.buzz"
    override var name = "Cinevez"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/home/" to "Latest",
        "$mainUrl/Cinevez/genre/documentary/" to "Documentary",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = pagedUrl(request.data, page)
        val results = parseCards(app.get(url).document)
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

        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - Cinevez")
            ?: url.substringAfterLast('/').replace('-', ' ')

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst(".post-image img, .entry-content img, article img, img.wp-post-image")
                    ?.attr("data-src")
                    ?.ifBlank { null }
                ?: document.selectFirst(".post-image img, .entry-content img, article img, img.wp-post-image")
                    ?.attr("src")
        )

        val genreText = document.fieldValue("Genre")
        val actorText = document.fieldValue("Actor")
        val releaseText = document.fieldValue("Release")

        val genres = genreText
            ?.split(Regex("\\s*[|,]\\s*"))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val actors = actorText
            ?.split(Regex("\\s*[|,]\\s*"))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val year = Regex("\\b(19|20)\\d{2}\\b")
            .find(releaseText.orEmpty())?.value?.toIntOrNull()
            ?: Regex("\\b(19|20)\\d{2}\\b").find(title)?.value?.toIntOrNull()

        val metaDescription = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("meta[name=description]")?.attr("content")

        val plot = metaDescription?.takeIf { it.length >= 60 }
            ?: document.select(".entry-content p, .post-content p, article p, main p")
                .map { it.text().trim() }
                .firstOrNull {
                    it.length >= 80 &&
                        !it.startsWith("Release date", ignoreCase = true) &&
                        !it.contains("Frequently Asked Questions", ignoreCase = true)
                }

        val recommendations = parseCards(document).filterNot { it.url == url }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = genres
            this.recommendations = recommendations
            addActors(actors)
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
                element.attr(if (element.hasAttr("src")) "src" else "href")
                    .takeIf { it.isNotBlank() }
            }
            .map { fixUrl(it) }
            .filter { isAuthorizedEmbed(it) }
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

    private fun pagedUrl(base: String, page: Int): String {
        if (page <= 1) return base
        return "${base.trimEnd('/')}/page/$page/"
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val selectors = listOf(
            "article h2 a[href]",
            "article h3 a[href]",
            ".post-item h2 a[href]",
            ".post-item h3 a[href]",
            "h2 a[href*='/Cinevez/']",
            "h3 a[href*='/Cinevez/']",
        )

        val seen = mutableSetOf<String>()
        val output = mutableListOf<SearchResponse>()

        document.select(selectors.joinToString(",")).forEach { anchor ->
            val result = anchor.toSearchResult() ?: return@forEach
            if (seen.add(result.url)) output.add(result)
        }

        return output
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val hrefRaw = attr("href").trim()
        if (hrefRaw.isBlank()) return null

        val href = fixUrl(hrefRaw)
        val path = runCatching { URI(href).path.orEmpty() }.getOrDefault(href)
        if (!path.contains("/Cinevez/", ignoreCase = true)) return null
        if (
            path.contains("/genre/", ignoreCase = true) ||
            path.contains("/release/", ignoreCase = true) ||
            path.contains("/category/", ignoreCase = true)
        ) return null

        val card = closest("article")
            ?: parents().firstOrNull { parent ->
                parent.classNames().any { cls ->
                    cls.contains("post", ignoreCase = true) ||
                        cls.contains("item", ignoreCase = true) ||
                        cls.contains("movie", ignoreCase = true)
                }
            }
            ?: parent()

        val title = text().trim().ifBlank {
            card?.selectFirst("h2, h3")?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) return null

        val image = card?.selectFirst("img")
        val poster = image?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: image?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
            ?: image?.attr("src")?.takeIf { it.isNotBlank() }

        val text = card?.text().orEmpty()
        val qualityName = Regex("(?i)\\b(2160p|1080p|720p|480p|360p|4k)\\b")
            .find(text)?.value

        return newMovieSearchResponse(title, href, TvType.Movie) {
            posterUrl = fixUrlNull(poster)
            quality = getQualityFromString(qualityName)
        }
    }

    private fun Document.fieldValue(label: String): String? {
        val fieldRegex = Regex("^\\s*${Regex.escape(label)}\\s*:\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE)

        select("p, li, div, span").forEach { element ->
            val own = element.ownText().trim()
            val match = fieldRegex.find(own)
            if (match != null) {
                return match.groupValues[1].trim().takeIf { it.isNotBlank() }
            }

            val combined = element.text().trim()
            if (combined.length <= 240) {
                val combinedMatch = fieldRegex.find(combined)
                if (combinedMatch != null) {
                    return combinedMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
                }
            }
        }

        return null
    }

    private fun isAuthorizedEmbed(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        return host == "youtu.be" ||
            host.endsWith(".youtube.com") ||
            host == "youtube.com" ||
            host.endsWith(".vimeo.com") ||
            host == "vimeo.com" ||
            host.endsWith(".archive.org") ||
            host == "archive.org"
    }
}
