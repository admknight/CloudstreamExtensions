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

class CinemaluxeProvider : MainAPI() {
    override var mainUrl = "https://cinemalux.baby"
    override var name = "Cinemaluxe"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Recently Added",
        "$mainUrl/genre/hollywood/page/" to "Hollywood",
        "$mainUrl/genre/south-indian-movies/page/" to "South Indian",
        "$mainUrl/genre/marvel/page/" to "Marvel",
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
        val suffix = if (page <= 1) "" else "page/$page/"
        val document = app.get("$mainUrl/$suffix?s=${query.encodeUri()}").document
        return parseCards(document).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("div.data > h1, h1")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: url.substringAfterLast('/').replace('-', ' ')

        val poster = fixUrlNull(
            document.selectFirst("div.poster img")?.imageUrl()
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("article img, main img")?.imageUrl()
        )

        val plot = document.selectFirst("div.wp-content, .wp-content, .description, .sbox .wp-content")
            ?.text()?.trim()?.takeIf { it.length >= 40 }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val year = Regex("""\b(19|20)\d{2}\b""")
            .find(
                document.selectFirst(".extra, .date, .data, h1")?.text().orEmpty() + " " + title
            )?.value?.toIntOrNull()

        val tags = document.select(
            ".sgeneros a, .genres a, a[href*='/genre/']"
        ).map { it.text().trim() }.filter { it.isNotBlank() }.distinct()

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

        val links = document.select("iframe[src], video[src], video source[src], a[href]")
            .mapNotNull { element ->
                val raw = when {
                    element.hasAttr("src") -> element.attr("src")
                    element.hasAttr("href") -> element.attr("href")
                    else -> ""
                }.trim()
                raw.takeIf { it.isNotBlank() }
            }
            .map { fixUrl(it) }
            .filter { isSupportedPublicEmbed(it) }
            .distinct()

        links.forEach { link ->
            loadExtractor(link, mainUrl, subtitleCallback, callback)
        }

        return links.isNotEmpty()
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val candidates = document.select(
            "article.item, div.item, div.result-item, .items article, .result-item"
        )

        val seen = mutableSetOf<String>()
        val results = mutableListOf<SearchResponse>()

        candidates.forEach { card ->
            val parsed = card.toSearchResult() ?: return@forEach
            if (seen.add(parsed.url)) results.add(parsed)
        }

        if (results.isEmpty()) {
            document.select("a[href*='/movies/'], a[href*='/tvshows/']").forEach { anchor ->
                val parsed = anchor.toSearchResult() ?: return@forEach
                if (seen.add(parsed.url)) results.add(parsed)
            }
        }

        return results
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (tagName() == "a") this else selectFirst(
            "a[href*='/movies/'], a[href*='/tvshows/'], a[href]"
        ) ?: return null

        val href = anchor.attr("href").trim().takeIf { it.isNotBlank() }?.let { fixUrl(it) }
            ?: return null

        val path = runCatching { URI(href).path.orEmpty() }.getOrDefault("")
        if (
            !path.contains("/movies/", ignoreCase = true) &&
            !path.contains("/tvshows/", ignoreCase = true)
        ) return null

        val card = if (tagName() == "a") {
            closest("article") ?: parent()
        } else {
            this
        }

        val image = card?.selectFirst("img") ?: anchor.selectFirst("img")
        val title = image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
            ?: card?.selectFirst(".title, h2, h3")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: anchor.attr("title").trim().takeIf { it.isNotBlank() }
            ?: anchor.text().trim().takeIf { it.isNotBlank() }
            ?: return null

        val poster = image?.imageUrl()
        val qualityText = card?.selectFirst(".quality, .mli-quality, .set")?.text()
            ?: card?.text().orEmpty()
        val qualityName = Regex("""(?i)\b(2160p|1080p|720p|480p|360p|4k)\b""")
            .find(qualityText)?.value

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
