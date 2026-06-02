package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class WatchAsianProvider : MainAPI() {
    override var mainUrl = "https://watchasian.cx"
    override var name = "WatchAsian"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val headers = mapOf("X-Requested-By" to mainUrl)
        val doc = app.get(mainUrl, headers = headers).document
        val rowPair = mutableListOf<Pair<String, String>>()
        doc.select("div.block-tab").forEach { tab ->
            tab.select("ul.tab > li").forEach { row ->
                val link = row.attr("data-tab")
                val title = row.text()
                if (link.isNotEmpty()) rowPair.add(Pair(title, link))
            }
        }

        val sections = rowPair.mapNotNull { row ->
            val main = doc.selectFirst("div.tab-content.${row.second}, div.tab-content.${row.second}.selected") ?: return@mapNotNull null
            val title = row.first
            val inner = main.select("li")
            if (inner.isEmpty()) return@mapNotNull null

            val results = inner.mapNotNull {
                val a = it.selectFirst("a")
                val link = fixUrlNull(a?.attr("href")) ?: return@mapNotNull null
                val image = fixUrlNull(a?.selectFirst("img")?.attr("data-original")) ?: ""
                val rName = a?.selectFirst("h3.title")?.text() ?: a?.text() ?: "<Untitled>"
                newMovieSearchResponse(rName, link, TvType.TvSeries) { 
                    this.posterUrl = image 
                }
            }.distinctBy { it.url }
            
            HomePageList(title, results)
        }

        return newHomePageResponse(sections)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?type=movies&keyword=$query"
        val document = app.get(url).document
        val items = document.select("div.block.tab-container > div > ul > li")

        return items.mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val link = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val title = it.selectFirst("h3.title")?.text() ?: return@mapNotNull null
            if (title.isEmpty()) return@mapNotNull null
            val image = fixUrlNull(a.selectFirst("img")?.attr("data-original"))

            newMovieSearchResponse(title, link, TvType.Movie) { 
                this.posterUrl = image 
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val body = app.get(url).document
        val isDramaDetail = url.contains("/drama-detail/")
        var poster: String? = null
        var title = ""
        var descript: String? = null
        var year: Int? = null
        var tags: List<String>? = null
        
        if (isDramaDetail) {
            val details = body.selectFirst("div.details")
            val info = details?.selectFirst("div.info")
            poster = fixUrlNull(details?.selectFirst("div.img > img")?.attr("src"))
            title = info?.selectFirst("h1")?.text() ?: ""
            descript = info?.text()

            info?.select("p")?.forEach { p ->
                val caption = p.selectFirst("span")?.text()?.trim()?.lowercase()?.removeSuffix(":")?.trim() ?: return@forEach
                when (caption) {
                    "genre" -> tags = p.select("a").map { it.text().trim() }
                    "released" -> year = p.selectFirst("a")?.text()?.trim()?.toIntOrNull()
                }
            }
        } else {
            poster = body.selectFirst("meta[itemprop=\"image\"]")?.attr("content") ?: ""
            title = body.selectFirst("div.block.watch-drama h1")?.text() ?: ""
            descript = body.selectFirst("meta[name=\"description\"]")?.attr("content")
        }
        
        if (year == null) {
            year = Regex("(\\d{4})").find(title)?.value?.toIntOrNull()
        }

        val episodeList = body.select("ul.list-episode-item-2.all-episode > li").mapNotNull { ep ->
            val a = ep.selectFirst("a") ?: return@mapNotNull null
            val epLink = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val count = Regex("episode-(\\d+)").find(epLink)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            newEpisode(epLink) {
                this.episode = count
                this.posterUrl = poster
            }
        }

        return if (episodeList.size == 1) {
            val streamLink = getServerLinks(episodeList[0].data)
            newMovieLoadResponse(title.trim().removeSuffix("Episode 1"), url, TvType.Movie, streamLink) {
                this.posterUrl = poster
                this.year = year
                this.plot = descript
                this.tags = tags
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodeList.reversed()) {
                this.posterUrl = poster
                this.year = year
                this.plot = descript
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
        val links = if (data.startsWith(mainUrl)) getServerLinks(data) else data
        val items = try { parseJson<List<String>>(links) } catch (_: Exception) { emptyList() }
        
        items.forEach { item ->
            val url = fixUrl(item.trim())
            when {
                url.startsWith("https://asianembed.io") || url.startsWith("https://asianload.io") || url.contains("/streaming.php?") -> {
                    val iv = "9262859232435825"
                    val secretKey = "93422192433952489752342908585752"
                    Vidstream.extractVidstream(url, this.name, callback, iv, secretKey, secretKey, isUsingAdaptiveKeys = false, isUsingAdaptiveData = false)
                    AsianEmbedHelper.getUrls(url, subtitleCallback, callback)
                }
                url.startsWith("https://embedsito.com") -> {
                    val extractor = XStreamCdn()
                    extractor.domainUrl = "embedsito.com"
                    extractor.getSafeUrl(url, subtitleCallback = subtitleCallback, callback = callback)
                }
                else -> loadExtractor(url, mainUrl, subtitleCallback, callback)
            }
        }
        return items.isNotEmpty()
    }

    private suspend fun getServerLinks(url: String): String {
        val doc = app.get(url, referer = mainUrl).document
        return doc.select("div.anime_muti_link > ul > li").mapNotNull {
            fixUrlNull(it.attr("data-video"))
        }.toJson()
    }
}
