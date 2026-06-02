package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup

class KdramaHoodProvider : MainAPI() {
    override var mainUrl = "https://kdramahood.com"
    override var name = "KDramaHood"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    private data class ResponseDatas(
        @JsonProperty("label") val label: String,
        @JsonProperty("file") val file: String
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/home2").document
        val home = ArrayList<HomePageList>()

        val recentlyInner = doc.selectFirst("div.peliculas")
        val recentlyAddedTitle = recentlyInner?.selectFirst("h1")?.text() ?: "Recently Added"
        val recentlyAdded = recentlyInner?.select("div.item_2.items > div.fit.item")?.mapNotNull {
            val innerA = it.selectFirst("div.image > a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val image = fixUrlNull(innerA.selectFirst("img")?.attr("src"))

            val innerData = it.selectFirst("div.data")
            val title = innerData?.selectFirst("h1")?.text() ?: return@mapNotNull null
            val year = try {
                val yearText = innerData.selectFirst("span.titulo_o")?.text()?.takeLast(11)?.trim()?.take(4) ?: ""
                Regex("\\((\\d+)").find(yearText)?.groupValues?.get(1)?.toIntOrNull()
            } catch (e: Exception) { null }

            newMovieSearchResponse(title, link, TvType.TvSeries) { 
                this.posterUrl = image
                this.year = year
            }
        }?.distinctBy { it.url } ?: emptyList()
        
        if (recentlyAdded.isNotEmpty()) home.add(HomePageList(recentlyAddedTitle, recentlyAdded))
        return newHomePageResponse(home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        val items = document.select("div.item_1.items > div.item")

        return items.mapNotNull {
            val innerA = it.selectFirst("div.boxinfo > a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val title = innerA.selectFirst("span.tt")?.text() ?: return@mapNotNull null

            val year = it.selectFirst("span.year")?.text()?.toIntOrNull()
            val image = fixUrlNull(it.selectFirst("div.image > img")?.attr("src"))

            newMovieSearchResponse(title, link, TvType.Movie) { 
                this.posterUrl = image
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val inner = doc.selectFirst("div.central")

        val title = inner?.selectFirst("h1")?.text() ?: ""
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")) ?: ""
        val info = inner?.selectFirst("div#info")
        val descript = inner?.selectFirst("div.contenidotv > div > p")?.text()
        
        val year = try {
            val startLink = "https://kdramahood.com/drama-release-year/"
            var res: Int? = null
            info?.select("div.metadatac")?.forEach {
                val yearLink = it.selectFirst("a")?.attr("href") ?: return@forEach
                if (yearLink.startsWith(startLink)) {
                    res = yearLink.removePrefix(startLink).replace("/", "").toIntOrNull()
                }
            }
            res
        } catch (e: Exception) { null }

        val recs = doc.select("div.sidebartv > div.tvitemrel").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val aUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val aImg = a.selectFirst("img")
            val aCover = fixUrlNull(aImg?.attr("src")) ?: fixUrlNull(aImg?.attr("data-src"))
            val aNameYear = a.selectFirst("div.datatvrel")
            val aName = aNameYear?.selectFirst("h4")?.text() ?: aImg?.attr("alt") ?: return@mapNotNull null
            val aYear = Regex("(\\d{4})").find(aName)?.value?.toIntOrNull()
            newMovieSearchResponse(aName, aUrl, TvType.Movie) { 
                this.posterUrl = aCover
                this.year = aYear
            }
        }

        val episodeList = inner?.select("ul.episodios > li")?.mapNotNull { ep ->
            val count = ep.selectFirst("div.numerando")?.text()?.toIntOrNull() ?: 0
            val innerA = ep.selectFirst("div.episodiotitle > a") ?: return@mapNotNull null
            val epLink = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            
            val listOfLinks = mutableListOf<String>()
            if (epLink.isNotBlank()) {
                val epVidDoc = app.get(epLink, referer = mainUrl).document
                val script = epVidDoc.selectFirst("div.player_nav > script")?.html()
                if (script != null) {
                    val content = script.replace("ifr_target.src =", "<div>").replace("';", "</div>")
                    Jsoup.parse(content).select("div").forEach { em ->
                        val href = em.html().trim().removePrefix("'")
                        if (href.isNotBlank()) listOfLinks.add(fixUrl(href))
                    }
                }
                epVidDoc.select("div.embed2").forEach { defsrc ->
                    val scriptString = defsrc.toString()
                    if (scriptString.contains("sources: [{")) {
                        "(?<=playerInstance2.setup\\()([\\s\\S]*?)(?=\\);)".toRegex()
                            .find(scriptString)?.value?.let { itemjs ->
                            listOfLinks.add("$mainUrl$itemjs")
                        }
                    }
                }
            }
            newEpisode(listOfLinks.distinct().toJson()) {
                this.episode = count
                this.posterUrl = poster
            }
        } ?: emptyList()

        return if (episodeList.size == 1) {
            newMovieLoadResponse(title, url, TvType.Movie, episodeList[0].data) {
                this.posterUrl = poster
                this.year = year
                this.plot = descript
                this.recommendations = recs
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodeList.reversed()) {
                this.posterUrl = poster
                this.year = year
                this.plot = descript
                this.recommendations = recs
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val items = try { parseJson<List<String>>(data) } catch (_: Exception) { emptyList() }
        items.forEach { item ->
            if (item.isBlank()) return@forEach
            if (item.startsWith(mainUrl)) {
                val text = item.removePrefix(mainUrl)
                runCatching {
                    "(?<=sources: )([\\s\\S]*?)(?<=])".toRegex().find(text)?.value?.let { vid ->
                        parseJson<List<ResponseDatas>>(vid).forEach { src ->
                            callback(newExtractorLink(name, name, src.file, INFER_TYPE) {
                                this.quality = getQualityFromName(src.label)
                                this.referer = mainUrl
                            })
                        }
                    }
                }
                runCatching {
                    "(?<=tracks: )([\\s\\S]*?)(?<=])".toRegex().find(text)?.value?.let { sub ->
                        val subtext = sub.replace("file:", "\"file\":").replace("label:", "\"label\":").replace("kind:", "\"kind\":")
                        parseJson<List<ResponseDatas>>(subtext).forEach { src ->
                            subtitleCallback(SubtitleFile(src.label, src.file))
                        }
                    }
                }
            } else {
                val url = fixUrl(item.trim())
                when {
                    url.startsWith("https://asianembed.io") -> AsianEmbedHelper.getUrls(url, subtitleCallback, callback)
                    url.startsWith("https://embedsito.com") -> {
                        val extractor = XStreamCdn()
                        extractor.domainUrl = "embedsito.com"
                        extractor.getUrl(url).forEach { callback.invoke(it) }
                    }
                    else -> loadExtractor(url, mainUrl, subtitleCallback, callback)
                }
            }
        }
        return items.isNotEmpty()
    }
}
