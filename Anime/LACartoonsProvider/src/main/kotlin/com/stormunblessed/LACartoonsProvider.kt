package com.stormunblessed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document

class LACartoonsProvider:MainAPI() {
    override var mainUrl = "https://www.lacartoons.com"
    override var name = "LACartoons"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.TvSeries
    )

    private fun Document.toSearchResult():List<SearchResponse>{
        return this.select(".categorias .conjuntos-series a").map {
            val title = it.selectFirst("p.nombre-serie")?.text() ?: ""
            val href = fixUrl(it.attr("href"))
            val img = fixUrlNull(it.selectFirst("img")?.attr("src"))
            newTvSeriesSearchResponse(title, href, TvType.Cartoon){
                this.posterUrl = img
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        val home = soup.toSearchResult()
        items.add(HomePageList("Series", home))
        return newHomePageResponse(items)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?utf8=✓&Titulo=$query").document
        return doc.toSearchResult()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("h2.text-center")?.text() ?: ""
        val description = doc.selectFirst(".informacion-serie-seccion p:contains(Reseña)")?.text()?.substringAfter("Reseña:")?.trim()
        val poster = doc.selectFirst(".imagen-serie img")?.attr("src")
        val backposter = doc.selectFirst("img.fondo-serie-seccion")?.attr("src")
        val episodes = doc.select("ul.listas-de-episodion li").mapNotNull {
            val regexep = Regex("Capitulo.(\\d+)|Capitulo.(\\d+)\\-")
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href")
            val name = a.text().replace(regexep, "").replace("-","").trim()
            val seasonnum = href.substringAfter("t=", "").toIntOrNull()
            val epnum = regexep.find(a.text())?.groupValues?.get(1)?.toIntOrNull()
            
            newEpisode(fixUrl(href)) {
                this.name = name
                this.season = seasonnum
                this.episode = epnum
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes){
            this.posterUrl = fixUrlNull(poster)
            this.backgroundPosterUrl = fixUrlNull(backposter)
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data).document
        res.select(".serie-video-informacion iframe").forEach {
            val link = it.attr("src")?.replace("https://short.ink/","https://abysscdn.com/?v=") ?: return@forEach
            loadExtractor(link, data, subtitleCallback, callback)
        }
        return true
    }
}
