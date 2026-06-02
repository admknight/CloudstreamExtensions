package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup
import java.net.URI

open class VidstreamProviderTemplate : MainAPI() {
    open val homePageUrlList = listOf<String>()
    open val vidstreamExtractorUrl: String? = null

    open val iv: String? = null
    open val secretKey: String? = null
    open val secretDecryptKey: String? = null
    open val isUsingAdaptiveKeys: Boolean = false
    open val isUsingAdaptiveData: Boolean = false

    override val hasQuickSearch = false
    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search.html?keyword=$query"
        val soup = app.get(link).document

        return soup.select(".listing.items > .video-block").mapNotNull { li ->
            val href = fixUrl(li.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val poster = li.selectFirst("img")?.attr("src")
            val title = li.selectFirst(".name")?.text() ?: ""
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()

            val cleanTitle = if (!title.contains("Episode")) title else title.split("Episode")[0].trim()
            
            newMovieSearchResponse(cleanTitle, href, TvType.TvSeries) { 
                this.posterUrl = poster 
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document

        val hTitle = soup.selectFirst("h1,h2,h3")?.text() ?: ""
        val title = if (!hTitle.contains("Episode")) hTitle else hTitle.split("Episode")[0].trim()
        val description = soup.selectFirst(".post-entry")?.text()?.trim()
        var poster: String? = null
        var year: Int? = null

        val episodes = soup.select(".listing.items.lists > .video-block").mapIndexed { _, li ->
            val epName = li.selectFirst(".name")?.text() ?: ""
            val epTitle = if (epName.contains("Episode")) "Episode " + epName.split("Episode")[1].trim() else epName
            val epThumb = li.selectFirst("img")?.attr("src")
            val epDate = li.selectFirst(".meta > .date")?.text() ?: ""

            if (poster == null) {
                poster = li.selectFirst("img")?.attr("onerror")?.split("=")?.getOrNull(1)?.replace(Regex("[';]"), "")
            }

            val epNum = Regex("""Episode (\d+)""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
            if (year == null) {
                year = epDate.split("-").getOrNull(0)?.toIntOrNull()
            }
            newEpisode(li.selectFirst("a")?.attr("href") ?: "") {
                this.episode = epNum
                this.posterUrl = epThumb
                addDate(epDate)
            }
        }.reversed()

        val tvType = if (episodes.size == 1 && (episodes[0].name ?: "") == title) TvType.Movie else TvType.TvSeries

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.showStatus = ShowStatus.Ongoing
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.getOrNull(0)?.data ?: "") {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()
        homePageUrlList.forEach { url ->
            val document = app.get(url, timeout = 20).document
            document.select("div.main-inner").forEach { inner ->
                val title = inner.selectFirst(".widget-title")?.text()?.trim() ?: "Main"
                val elements = inner.select(".video-block").mapNotNull {
                    val link = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                    val image = it.selectFirst(".picture > img")?.attr("src")
                    val name = it.selectFirst("div.name")?.text()?.trim()?.replace(Regex("""[Ee]pisode \d+"""), "") ?: ""
                    val isSeries = name.contains("Season") || name.contains("Episode")

                    newMovieSearchResponse(name, link, if (isSeries) TvType.TvSeries else TvType.Movie) {
                        this.posterUrl = image
                    }
                }
                homePageList.add(HomePageList(title, elements))
            }
        }
        return newHomePageResponse(homePageList)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val iframeLink = app.get(data).document.selectFirst("iframe")?.attr("src") ?: return false

        val vidstreamObject = Vidstream(vidstreamExtractorUrl ?: mainUrl)
        val id = Regex("""id=([^&]*)""").find(iframeLink)?.groupValues?.get(1)

        if (id != null) {
            vidstreamObject.getUrl(id, isCasting, subtitleCallback, callback)
        }

        val soup = app.get(fixUrl(iframeLink)).document
        val servers = soup.select(".list-server-items > .linkserver").mapNotNull { li ->
            val vUrl = li.attr("data-video")
            if (vUrl.isNotEmpty()) Pair(li.text(), fixUrl(vUrl)) else null
        }
        
        servers.forEach { (sName, sUrl) ->
            if (sName.trim().equals("beta server", ignoreCase = true)) {
                val sourceRegex = Regex("""sources:[\W\w]*?file:\s*["'](.*?)["'][\W\w]*?label:\s*["'](.*?)["']""")
                val trackRegex = Regex("""tracks:[\W\w]*?file:\s*["'](.*?)["'][\W\w]*?label:\s*["'](.*?)["']""")

                val serverHtml = app.get(sUrl, headers = mapOf("referer" to iframeLink)).text
                sourceRegex.findAll(serverHtml).forEach { match ->
                    val label = match.groupValues.getOrNull(2) ?: ""
                    callback.invoke(newExtractorLink(this.name, if (label.isNotBlank()) "${this.name} $label" else this.name, match.groupValues[1], INFER_TYPE) {
                        this.referer = sUrl
                        this.quality = getQualityFromName(label)
                    })
                }
                trackRegex.findAll(serverHtml).forEach { match ->
                    subtitleCallback.invoke(SubtitleFile(match.groupValues.getOrNull(2) ?: "Unknown", match.groupValues[1]))
                }
            }
        }
        return true
    }
}
