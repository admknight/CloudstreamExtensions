package com.admknight.movierulzhd

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import org.jsoup.nodes.Element
import java.net.URI
import java.text.Normalizer

open class Movierulzhd : MainAPI() {
    override var mainUrl: String = runBlocking {
        MovierulzhdPlugin.getDomains()?.movierulzhd ?: "https://123moviesfree9.cloud"
    }
    private var directUrl = ""
    override var name = "Movierulzhd"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "movies" to "Movies",
        "tvshows" to "TV Shows",
        "genre/netflix" to "Netflix",
        "genre/amazon-prime" to "Amazon Prime",
        "genre/Zee5" to "Zee5",
        "genre/sony-liv" to "Sony Liv",
        "genre/hotstar" to "Hotstar",
        "genre/jio-cinema" to "Jio Cinema",
        "seasons" to "Season",
        "episodes" to "Episode",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if(page == 1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episodes/") -> {
                val slug = Regex("(.+?)-season").find(uri.substringAfter("/episodes/"))?.groupValues?.get(1) ?: ""
                "$mainUrl/tvshows/$slug"
            }
            uri.contains("/seasons/") -> {
                val slug = Regex("(.+?)-season").find(uri.substringAfter("/seasons/"))?.groupValues?.get(1) ?: ""
                "$mainUrl/tvshows/$slug"
            }
            else -> uri
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("h3 > a") ?: return null
        val title = a.text().trim()
        val href = getProperLink(fixUrl(a.attr("href")))
        val poster = this.select("div.poster img").last()?.getImageAttr()
        val quality = getSearchQuality(this.select("span.quality").text())
        val scoreValue = Score.from10(this.select("div.rating").text())
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.quality = quality
            this.score = scoreValue
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").document
        return document.select("div.result-item").mapNotNull {
            val a = it.selectFirst("div.title > a") ?: return@mapNotNull null
            val title = a.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
            val href = getProperLink(a.attr("href"))
            val poster = it.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val document = request.document
        directUrl = getBaseUrl(request.url)
        
        val title = document.selectFirst("div.data > h1")?.text()?.trim() ?: ""
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.getImageAttr())
        val background = fixUrlNull(document.selectFirst(".playbox img.cover")?.getImageAttr()) ?: poster
        
        val tags = document.select("div.sgeneros > a").map { it.text() }
        val year = Regex(",\\s?(\\d+)").find(document.select("span.date").text())?.groupValues?.get(1)?.toIntOrNull()
        
        val isSeries = document.select("ul#section > li:nth-child(1)").text().contains("Episodes") || 
                       document.select("ul#playeroptionsul li span.title").text().contains(Regex("Episode\\s+\\d+|EP\\d+|PE\\d+|S\\d{2}|E\\d{2}"))
        
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie
        val description = document.select("div.wp-content > p").text().trim()
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val scoreValue = Score.from10(document.selectFirst("span.dt_rating_vgs")?.text())
        
        val actors = document.select("div.persons > div[itemprop=actor]").map {
            Actor(it.select("meta[itemprop=name]").attr("content"), it.select("img:last-child").attr("src"))
        }

        val recommendations = document.select("div.owl-item").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val rName = a.attr("href").removeSuffix("/").split("/").last()
            val rHref = a.attr("href")
            val rPoster = it.selectFirst("img")?.getImageAttr()
            newTvSeriesSearchResponse(rName, rHref, TvType.TvSeries) { this.posterUrl = rPoster }
        }

        if (tvType == TvType.TvSeries) {
            val episodes = if (document.select("ul.episodios > li").isNotEmpty()) {
                document.select("ul.episodios > li").mapNotNull { li ->
                    val a = li.selectFirst("a") ?: return@mapNotNull null
                    val numText = li.selectFirst("div.numerando")?.text()?.replace(" ", "") ?: ""
                    newEpisode(a.attr("href")) {
                        this.name = fixTitle(li.selectFirst("div.episodiotitle > a")?.text()?.trim() ?: "")
                        this.episode = numText.split("-").lastOrNull()?.toIntOrNull()
                        this.season = numText.split("-").firstOrNull()?.toIntOrNull()
                        this.posterUrl = li.selectFirst("div.imagen > img")?.getImageAttr()
                    }
                }
            } else {
                val list = document.select("ul#playeroptionsul > li")
                val items = if (list.toString().contains("Super")) list.drop(1) else list
                items.map { li ->
                    val name = li.selectFirst("span.title")?.text()
                    newEpisode(LinkData(name, li.attr("data-type"), li.attr("data-post"), li.attr("data-nume")).toJson()) {
                        this.name = name
                    }
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = scoreValue
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = scoreValue
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    private fun getBaseUrl(url: String): String = URI(url).let { "${it.scheme}://${it.host}" }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("{")) {
            val loadData = tryParseJson<LinkData>(data) ?: return false
            runCatching {
                val res = app.post(
                    url = "$directUrl/wp-admin/admin-ajax.php",
                    data = mapOf("action" to "doo_player_ajax", "post" to "${loadData.post}", "nume" to "${loadData.nume}", "type" to "${loadData.type}"),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>().embed_url
                if (!res.contains("youtube")) loadCustomExtractor(name, res, "$directUrl/", subtitleCallback, callback)
            }
        } else {
            val doc = app.get(data).document
            doc.select("ul#playeroptionsul > li").amap { li ->
                runCatching {
                    val res = app.post(
                        url = "$directUrl/wp-admin/admin-ajax.php",
                        data = mapOf("action" to "doo_player_ajax", "post" to li.attr("data-post"), "nume" to li.attr("data-nume"), "type" to li.attr("data-type")),
                        referer = data,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                    ).parsed<ResponseHash>().embed_url
                    if (!res.contains("youtube")) {
                        if (res.contains("/#")) VidStack().getUrl(res, "", subtitleCallback, callback)
                        else loadExtractor(res, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return this.attr("abs:data-src").ifBlank { this.attr("abs:data-lazy-src") }.ifBlank { this.attr("abs:srcset").substringBefore(" ") }.ifBlank { this.attr("abs:src") }
    }

    private suspend fun loadCustomExtractor(name: String? = null, url: String, referer: String? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(newExtractorLink(name ?: link.source, name ?: link.name, link.url, INFER_TYPE) {
                    this.quality = if (link.name == "VidSrc") Qualities.P1080.value else link.quality
                    this.referer = link.referer
                    this.headers = link.headers
                })
            }
        }
    }

    data class LinkData(val tag: String? = null, val type: String? = null, val post: String? = null, val nume: String? = null)

    fun getSearchQuality(check: String?): SearchQuality? {
        val u = Normalizer.normalize(check ?: return null, Normalizer.Form.NFKC).lowercase()
        val patterns = listOf(
            Regex("\\b(4k|uhd)\\b") to SearchQuality.FourK,
            Regex("\\b(hdts|hdcam|hdtc|camrip|cam)\\b") to SearchQuality.HdCam,
            Regex("\\b(web[- ]?dl|webrip|webdl)\\b") to SearchQuality.WebRip,
            Regex("\\b(bluray|bdrip|blu[- ]?ray)\\b") to SearchQuality.BlueRay,
            Regex("\\b(1080p|fullhd|hdrip|hdtv|hd)\\b") to SearchQuality.HD,
            Regex("\\b720p\\b") to SearchQuality.SD,
        )
        return patterns.firstNotNullOfOrNull { (regex, quality) -> quality.takeIf { regex.containsMatchIn(u) } }
    }

    data class ResponseHash(@JsonProperty("embed_url") val embed_url: String)
}
