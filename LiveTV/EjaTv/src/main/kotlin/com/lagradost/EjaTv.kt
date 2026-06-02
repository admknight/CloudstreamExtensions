package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.nodes.Element

class EjaTv : MainAPI() {
    override var mainUrl = "https://ejatv.com"
    override var name = "Eja.tv"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val home = document.select("div.card").mapNotNull {
            it.toSearchResponse()
        }
        return newHomePageResponse(listOf(HomePageList("Channels", home)), false)
    }

    private fun Element.toSearchResponse(): LiveSearchResponse? {
        val link = this.select("div.alternative a").last() ?: return null
        val href = fixUrl(link.attr("href"))
        val img = this.selectFirst("div.thumb img")
        val langCode = this.selectFirst(".card-title > a")?.attr("href")?.removePrefix("?country=")
            ?.replace("int", "eu") //international -> European Union 🇪🇺
        return newLiveSearchResponse(
            // Kinda hack way to get the title
            img?.attr("alt")?.replaceFirst("Watch ", "") ?: return null,
            href,
            TvType.Live,
            false
        ) {
            this.posterUrl = fixUrlNull(img.attr("src"))
            this.lang = langCode
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.card").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: ""
        val poster = document.selectFirst("div.thumb img")?.attr("src")
        val plot = document.selectFirst("div.description")?.text()
        
        val script = document.select("script").find { it.data().contains("var source =") }?.data()
        val streamUrl = script?.substringAfter("var source = \"")?.substringBefore("\"") ?: ""

        return newLiveStreamLoadResponse(title, url, streamUrl) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = data,
                type = INFER_TYPE
            ) {
                this.referer = mainUrl
            }
        )
        return true
    }
}
