package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class KimCartoonProvider : MainAPI() {
    override var mainUrl = "https://kimcartoon.li"
    override var name = "KimCartoon"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Cartoon)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = org.jsoup.Jsoup.parse(html)
        val items = ArrayList<HomePageList>()

        document.select(".big-section").forEach { section ->
            val title = section.selectFirst(".title")?.text() ?: "Untitled"
            val anime = section.select(".item").mapNotNull { item ->
                item.toSearchResult()
            }
            if (anime.isNotEmpty()) {
                items.add(HomePageList(title, anime))
            }
        }
        return newHomePageResponse(items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".name")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val poster = fixUrlNull(this.selectFirst("img")?.attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Cartoon) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/Search/Keyword?keyword=$query"
        val document = app.get(url).document
        return document.select(".list-cartoon .item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst(".title")?.text() ?: ""
        val poster = fixUrlNull(document.selectFirst(".right-info img")?.attr("src"))
        val description = document.selectFirst(".description")?.text()
        
        val episodes = document.select(".list-chapter li a").map {
            newEpisode(it.attr("href")) {
                this.name = it.text()
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Cartoon) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes.reversed())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Implementation for loading links
        return true
    }
}
