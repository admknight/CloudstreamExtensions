package com.admknight.vidstreambundle

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
        val mainbody = document.getElementsByTag("body")

        val homePageLists = mainbody.select("section.block_area.block_area_home")?.mapNotNull { main ->
                val title = main.select("h2.cat-heading").text() ?: "Main"
                val inner = main.select("div.flw-item") ?: return@mapNotNull null

                HomePageList(
                    title,
                    inner.mapNotNull {
                        val innerBody = it?.selectFirst("a")
                        // Fetch details
                        val link = fixUrlNull(innerBody?.attr("href")) ?: return@mapNotNull null
                        val image = fixUrlNull(it.select("img").attr("data-src")) ?: ""
                        val name = innerBody?.attr("title") ?: "<Untitled>"
                        newMovieSearchResponse(name, link, TvType.AsianDrama) {
                            this.posterUrl = image
                        }
                    }.distinctBy { c -> c.url })
            } ?: listOf()
        return newHomePageResponse(homePageLists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val document = app.get(url).document
        val posters = document.select("div.film-poster")


        return posters.mapNotNull {
            val innerA = it.select("a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val title = innerA.attr("title") ?: return@mapNotNull null
            val year =
                Regex(""".*\((\d{4})\)""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val imgSrc = it.select("img")?.attr("data-src") ?: return@mapNotNull null
            val image = fixUrlNull(imgSrc)

            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = image
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.anis-content")

        // Video details
        val poster = fixUrlNull(inner?.select("img.film-poster-img")?.attr("src")) ?: ""
        val title = inner?.select("h2.film-name.dynamic-name")?.text() ?: ""
        val year = if (title.length > 5) {
            title.substring(title.length - 5)
                .trim().trimEnd(')').toIntOrNull()
        } else {
            null
        }
        val descript = body?.firstOrNull()?.select("div.film-description.m-hide")?.text()
        val tags = inner?.select("div.item.item-list > a")
            ?.mapNotNull { it?.text()?.trim() ?: return@mapNotNull null }
        val recs = body.select("div.flw-item")?.mapNotNull {
            val a = it.select("a") ?: return@mapNotNull null
            val aUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val aImg = fixUrlNull(it.select("img")?.attr("data-src"))
            val aName = a.attr("title") ?: return@mapNotNull null
            val aYear = aName.trim().takeLast(5).removeSuffix(")").toIntOrNull()
            newMovieSearchResponse(aName, aUrl, TvType.Movie) {
                this.posterUrl = aImg
                this.year = aYear
            }
        }

        // Episodes Links
        val episodeUrl = body.select("a.btn.btn-radius.btn-primary.btn-play").attr("href")
        val episodeDoc = app.get(episodeUrl).document


        val episodeList = episodeDoc.select("div.ss-list.ss-list-min > a").mapNotNull { ep ->
            val episodeNumber = ep.attr("data-number").toIntOrNull()
            val epLink = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
            newEpisode(epLink) {
                this.episode = episodeNumber
            }
        }

        //If there's only 1 episode, consider it a movie.
        if (episodeList.size == 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, episodeList.first().data) {
                this.posterUrl = poster
                this.year = year
                this.plot = descript
                this.recommendations = recs
                this.tags = tags
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodeList) {
            this.posterUrl = poster
            this.year = year
            this.plot = descript
            this.recommendations = recs
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        println("DATATATAT $data")

        val document = app.get(data).document
        val iframeUrl = document.select("iframe").attr("src")
        val iframe = app.get(iframeUrl)
        val iframeDoc = iframe.document

        coroutineScope {
            launch {
                iframeDoc.select(".list-server-items > .linkserver")
                    .forEach { element ->
                        val status = element.attr("data-status") ?: return@forEach
                        if (status != "1") return@forEach
                        val extractorData = element.attr("data-video") ?: return@forEach
                        loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                    }
            }
            launch {
                val iv = "9262859232435825"
                val secretKey = "93422192433952489752342908585752"
                val secretDecryptKey = "93422192433952489752342908585752"
                Vidstream.extractVidstream(
                    iframe.url,
                    this@DramaSeeProvider.name,
                    callback,
                    iv,
                    secretKey,
                    secretDecryptKey,
                    isUsingAdaptiveKeys = false,
                    isUsingAdaptiveData = true,
                    iframeDocument = iframeDoc
                )
            }
        }
        return true
    }
}
