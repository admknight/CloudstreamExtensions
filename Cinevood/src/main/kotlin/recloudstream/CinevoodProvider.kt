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

class CinevoodProvider : MainAPI() {
    override var mainUrl = "https://cinevood.bingo"
    override var name = "Cinevood"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
    )

    override val mainPage = mainPageOf(
        mainUrl to "Latest",
        "$mainUrl/bollywood/" to "Bollywood",
        "$mainUrl/hollywood/" to "Hollywood",
        "$mainUrl/tamil/" to "Tamil",
        "$mainUrl/marathi/" to "Marathi",
        "$mainUrl/gujarati/" to "Gujarati",
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

        val rawTitle = document.selectFirst(
            ".cv-movie-title, #movie_title a, h1.entry-title, h1.single-title, h1"
        )?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: url.substringAfterLast('/').replace('-', ' ')

        val title = rawTitle
            .replace(Regex("""(?i)^download\s+"""), "")
            .replace(Regex("""(?i)\s*[|–-]\s*CineVood.*$"""), "")
            .trim()

        val poster = fixUrlNull(
            document.selectFirst(".cv-movie-poster-img")?.imageUrl()
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst(".entry-content img, article img, main img")?.imageUrl()
        )

        val plot = document.selectFirst(".cv-movie-overview-box")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: document.select(".entry-content p, article p, main p")
                .map { it.text().trim() }
                .firstOrNull { it.length >= 80 }

        val year = Regex("""\b(19|20)\d{2}\b""")
            .find(title + " " + document.selectFirst(".entry-content, article, main")?.text().orEmpty())
            ?.value?.toIntOrNull()

        val tags = document.select(
            "a[href*='/genre/'], a[rel~='category'], a[rel~='tag']"
        )
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it.length <= 40 }
            .distinct()
            .take(12)

        val recommendations = parseCards(document)
            .filterNot { it.url.trimEnd('/') == url.trimEnd('/') }
            .distinctBy { it.url }
            .take(20)

        val mediaType = if (
            Regex("""(?i)\b(season|series|S\d{1,2})\b""").containsMatchIn(title)
        ) TvType.TvSeries else TvType.Movie

        return newMovieLoadResponse(title, url, mediaType, url) {
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

        val selectors = listOf(
            ".pstr_box",
            "article.latestpost",
            "article",
            ".result-item",
            ".post",
            ".post-item",
            ".item",
            ".thumbnail",
            ".latest-movies",
            ".movie-item",
        ).joinToString(",")

        document.select(selectors).forEach { card ->
            val result = card.toSearchResult() ?: return@forEach
            if (seen.add(result.url)) output.add(result)
        }

        if (output.isEmpty()) {
            document.select("a[href][title], h2 a[href], h3 a[href]").forEach { anchor ->
                val result = anchor.toSearchResult() ?: return@forEach
                if (seen.add(result.url)) output.add(result)
            }
        }

        return output.take(100)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = if (tagName() == "a") this else
            selectFirst("h1 a[href], h2 a[href], h3 a[href], .title a[href], a[title][href], a[href]")
            ?: return null

        val hrefRaw = anchor.attr("href").trim()
        if (hrefRaw.isBlank() || hrefRaw == "#") return null

        val href = fixUrl(hrefRaw)
        val uri = runCatching { URI(href) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        if (host != "cinevood.bingo" && !host.endsWith(".cinevood.bingo")) return null

        val path = uri.path.orEmpty().trimEnd('/')
        if (path.isBlank()) return null
        if (
            path.startsWith("/category/", true) ||
            path.startsWith("/tag/", true) ||
            path.startsWith("/author/", true) ||
            path.startsWith("/page/", true)
        ) return null

        val card = if (tagName() == "a") closest("article") ?: parent() else this
        val image = card?.selectFirst("img") ?: anchor.selectFirst("img")

        val title = card?.selectFirst(".cv-movie-title, h2, h3, .title")?.text()?.trim()
            ?.takeIf { it.length >= 3 }
            ?: anchor.attr("title").trim().takeIf { it.length >= 3 }
            ?: image?.attr("alt")?.trim()?.takeIf { it.length >= 3 }
            ?: anchor.text().trim().takeIf { it.length >= 3 }
            ?: return null

        val cleanedTitle = title
            .replace(Regex("""(?i)^download\s+"""), "")
            .replace(Regex("""\[.*?]"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        if (cleanedTitle.length < 3) return null

        val poster = image?.imageUrl()
        val qualityName = Regex(
            """(?i)\b(2160p|1080p|720p|480p|360p|4k|web-dl|webrip|bluray|hdrip|hdtc|hdcam)\b"""
        ).find(card?.text().orEmpty() + " " + cleanedTitle)?.value

        val mediaType = if (
            Regex("""(?i)\b(season|series|S\d{1,2})\b""").containsMatchIn(cleanedTitle)
        ) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(cleanedTitle, href, mediaType) {
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
