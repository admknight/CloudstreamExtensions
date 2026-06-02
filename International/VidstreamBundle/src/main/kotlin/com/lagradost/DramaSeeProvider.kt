package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class DramaSeeProvider : MainAPI() {
    override var mainUrl = "https://dramasee.net"
    override var name = "DramaSee"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val headers = mapOf("X-Requested-By" to mainUrl)
        val document = app.get(mainUrl, headers = headers).document
        val mainBody = document.getElementsByTag("body")

        val sections = mainBody.select("section.block_area.block_area_home").mapNotNull { main ->
            val title = main.selectFirst("h2.cat-heading")?.text() ?: "Main"
            val inner = main.select("div.flw-item")
            if (inner.isEmpty()) return@mapNotNull null

            val results = inner.mapNotNull {
                val a = it.selectFirst("a")
                val link = fixUrlNull(a?.attr("href")) ?: return@mapNotNull null
                val image = fixUrlNull(it.selectFirst("img")?.attr("data-src")) ?: ""
                val rName = a?.attr("title") ?: "<Untitled>"
                newMovieSearchResponse(rName, link, TvType.AsianDrama) { 
                    this.posterUrl = image 
                }
            }.distinctBy { it.url }
            
            HomePageList(title, results)
        }

        return newHomePageResponse(sections)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val document = app.get(url).document
        val posters = document.select("div.film-poster")

        return posters.mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val link = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val title = a.attr("title") ?: return@mapNotNull null
            val year = Regex(""".*\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            val image = fixUrlNull(it.selectFirst("img")?.attr("data-src"))

            newMovieSearchResponse(title, link, TvType.Movie) { 
                this.posterUrl = image 
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.selectFirst("body")
        val content = doc.selectFirst("div.anis-content")

        val poster = fixUrlNull(content?.selectFirst("img.film-poster-img")?.attr("src")) ?: ""
        val title = content?.selectFirst("h2.film-name.dynamic-name")?.text() ?: ""
        val year = Regex("(\\d{4})").find(title)?.value?.toIntOrNull()
        
        val descript = body?.selectFirst("div.film-description.m-hide")?.text()
        val tags = content?.select("div.item.item-list > a")?.map { it.text().trim() }
        
        val recs = body?.select("div.flw-item")?.mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val aUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val aImg = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            val aName = a.attr("title") ?: return@mapNotNull null
            val aYear = Regex("(\\d{4})").find(aName)?.value?.toIntOrNull()
            newMovieSearchResponse(aName, aUrl, TvType.Movie) { 
                this.posterUrl = aImg
                this.year = aYear
            }
        }

        val episodeUrl = body?.selectFirst("a.btn.btn-radius.btn-primary.btn-play")?.attr("href") ?: ""
        val episodeDoc = app.get(episodeUrl).document

        val episodeList = episodeDoc.select("div.ss-list.ss-list-min > a").mapNotNull { ep ->
            val epNum = ep.attr("data-number").toIntOrNull()
            val epLink = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
            
            newEpisode(epLink) {
                this.episode = epNum
            }
        }

        return if (episodeList.size == 1) {
            newMovieLoadResponse(title, url, TvType.Movie, episodeList.first().data) {
                this.posterUrl = poster
                this.year = year
                this.plot = descript
                this.recommendations = recs
                this.tags = tags
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodeList) {
                this.posterUrl = poster
                this.year = year
                this.plot = descript
                this.recommendations = recs
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
        val document = app.get(data).document
        val iframeUrl = document.selectFirst("iframe")?.attr("src") ?: return false
        val iframe = app.get(iframeUrl)
        val iframeDoc = iframe.document

        runAllAsync({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    val status = element.attr("data-status")
                    if (status != "1") return@forEach
                    val extractorData = element.attr("data-video")
                    loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            val iv = "9262859232435825"
            val secretKey = "93422192433952489752342908585752"
            val secretDecryptKey = "93422192433952489752342908585752"
            Vidstream.extractVidstream(
                iframe.url,
                this.name,
                callback,
                iv,
                secretKey,
                secretDecryptKey,
                isUsingAdaptiveKeys = false,
                isUsingAdaptiveData = true,
                iframeDocument = iframeDoc
            )
        })
        return true
    }
}
