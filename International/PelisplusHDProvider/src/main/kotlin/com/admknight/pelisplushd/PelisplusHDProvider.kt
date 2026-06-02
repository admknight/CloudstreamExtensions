package com.admknight.pelisplushd

import android.webkit.URLUtil
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.nodes.Element

class PelisplusHDProvider : MainAPI() {
    override var mainUrl = "https://pelisplushd.bz"
    override var name = "PelisplusHD"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val document = app.get(mainUrl).document
        val map = mapOf(
            "Películas" to "#default-tab-1",
            "Series" to "#default-tab-2",
            "Anime" to "#default-tab-3",
            "Doramas" to "#default-tab-4",
        )
        map.forEach { (title, selector) ->
            val results = document.select(selector).select("a.Posters-link").map { it.toSearchResult() }
            items.add(HomePageList(title, results))
        }
        return newHomePageResponse(items)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select(".listing-content p").text()
        val href = this.select("a").attr("href")
        val posterUrl = fixUrl(this.select(".Posters-img").attr("src"))
        val isMovie = href.contains("/pelicula/")
        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=${query}"
        val document = app.get(url).document

        return document.select("a.Posters-link").map {
            val title = it.selectFirst(".listing-content p")?.text() ?: ""
            val href = it.selectFirst("a")?.attr("href") ?: ""
            val image = fixUrl(it.selectFirst(".Posters-img")?.attr("src") ?: "")
            val isMovie = href.contains("/pelicula/")

            if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = image }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = image }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document

        val title = soup.selectFirst(".m-b-5")?.text() ?: ""
        val description = soup.selectFirst("div.text-large")?.text()?.trim()
        val poster = fixUrl(soup.selectFirst(".img-fluid")?.attr("src") ?: "")
        
        val episodes = soup.select("div.tab-pane .btn").mapNotNull { li ->
            val href = li.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val epName = li.selectFirst(".btn-primary.btn-block")?.text()?.replace(Regex("(T(\\d+).*E(\\d+):)"), "")?.trim()
            
            val seasonInfo = href.substringAfter("temporada/").replace("/capitulo/", "-")
            val parts = seasonInfo.split("-").mapNotNull { it.toIntOrNull() }
            val season = parts.getOrNull(0)
            val episodeNum = parts.getOrNull(1)
            
            newEpisode(href) {
                this.name = epName
                this.season = season
                this.episode = episodeNum
            }
        }

        val year = soup.selectFirst(".p-r-15 .text-semibold")?.text()?.toIntOrNull()
        val tvType = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries
        val tags = soup.select(".p-h-15.text-center a span.font-size-18.text-info.text-semibold")
            .map { it.text().trim().replace(", ", "") }

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("div.player").forEach { script ->
            val content = script.data()
                .replace("https://api.mycdn.moe/furl.php?id=", "https://www.fembed.com/v/")
                .replace("https://api.mycdn.moe/sblink.php?id=", "https://streamsb.net/e/")
            
            fetchUrls(content).forEach { link ->
                val html = app.get(link).document.html()
                val regex = """(go_to_player|go_to_playerVast)\('(.*?)'""".toRegex()
                
                regex.findAll(html).forEach { match ->
                    val current = match.groupValues.get(2)
                    val realLink = if (URLUtil.isValidUrl(current)) {
                        fixUrl(current)
                    } else {
                        runCatching { base64Decode(current) }.getOrNull()
                    }

                    if (!realLink.isNullOrBlank()) {
                        if (realLink.contains("api.mycdn.moe/video/") || realLink.contains("api.mycdn.moe/embed.php?customid")) {
                            val doc = app.get(realLink).document
                            doc.select("div.ODDIV li").forEach { li ->
                                val encoded = li.attr("data-r")
                                val decoded = base64Decode(encoded)
                                    .replace(Regex("https://owodeuwu.xyz|https://sypl.xyz"), "https://embedsito.com")
                                    .replace(Regex(".poster.*"), "")
                                
                                val secondId = li.attr("onclick").substringAfter("go_to_player('").substringBefore("',")
                                loadExtractor(decoded, realLink, subtitleCallback, callback)
                                
                                val res2 = app.get("https://api.mycdn.moe/player/?id=$secondId", allowRedirects = false).document
                                val thirdLink = res2.selectFirst("body > iframe")?.attr("src")
                                    ?.replace(Regex("https://owodeuwu.xyz|https://sypl.xyz"), "https://embedsito.com")
                                    ?.replace(Regex(".poster.*"), "")
                                
                                if (thirdLink != null) loadExtractor(thirdLink, realLink, subtitleCallback, callback)
                            }
                        } else {
                            loadExtractor(realLink, data, subtitleCallback, callback)
                        }
                    }
                }
            }
        }
        return true
    }
}
