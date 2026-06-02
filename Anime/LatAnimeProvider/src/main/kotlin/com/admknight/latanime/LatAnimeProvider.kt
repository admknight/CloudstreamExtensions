package com.admknight.latanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import java.util.*

class LatAnimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("OVA") || t.contains("Especial") -> TvType.OVA
                t.contains("Pelicula") -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }

        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed
            else DubStatus.Subbed
        }
    }

    override var mainUrl = "https://latanime.org"
    override var name = "LatAnime"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.OVA, TvType.Anime)

    private val cloudflareKiller = CloudflareKiller()
    private suspend fun appGetChildMainUrl(url: String): NiceResponse {
        return app.get(url, interceptor = cloudflareKiller)
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
                Pair("$mainUrl/emision", "En emisión"),
                Pair("$mainUrl/animes?fecha=false&genero=false&letra=false&categoria=Película", "Peliculas"),
                Pair("$mainUrl/animes", "Animes"),
        )

        val items = ArrayList<HomePageList>()
        urls.forEach { (url, name) ->
            val home = appGetChildMainUrl(url).document.select("div.col-md-4.col-lg-3.col-xl-2.col-6.my-3").mapNotNull {
                val a = it.selectFirst("a")
                val title = a?.selectFirst("h3.my-1")?.text() ?: ""
                val poster = a?.selectFirst("img")?.attr("src") ?: ""
                val link = a?.attr("href") ?: return@mapNotNull null

                newAnimeSearchResponse(title, fixUrl(link), TvType.Anime) {
                    this.posterUrl = fixUrl(poster)
                    addDubStatus(getDubStatus(title))
                }
            }
            if (home.isNotEmpty()) items.add(HomePageList(name, home))
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return appGetChildMainUrl("$mainUrl/buscar?q=$query").document.select("div.col-md-4.col-lg-3.col-xl-2.col-6.my-3").mapNotNull {
            val a = it.selectFirst("a")
            val title = a?.selectFirst("h3.my-1")?.text() ?: ""
            val href = a?.attr("href") ?: return@mapNotNull null
            val image = a?.selectFirst("img")?.attr("src") ?: ""
            
            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(image)
                addDubStatus(getDubStatus(title))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = appGetChildMainUrl(url).document
        val poster = doc.selectFirst("div.series2 div.serieimgficha img.img-fluid2")?.attr("src") ?: ""
        val title = doc.selectFirst("div.col-lg-9.col-md-8 h2")?.text() ?: ""
        val description = doc.selectFirst("div.col-lg-9.col-md-8 p.my-2.opacity-75")?.text()?.replace("Ver menos", "") ?: ""
        val genres = doc.select("div.col-lg-9.col-md-8 a div.btn").map { it.text() }
        val status = when (doc.selectFirst("div.series2 div.serieimgficha div.my-2")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        
        val episodes = doc.select("div.row div.col-lg-9.col-md-8 div.row div a").map {
            val eName = it.selectFirst("div.cap-layout")?.text() ?: ""
            val link = it.attr("href") ?: ""
            newEpisode(link) { this.name = eName }
        }
        
        return newAnimeLoadResponse(title, url, getType(title)) {
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            this.showStatus = status
            this.plot = description
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        appGetChildMainUrl(data).document.select("li#play-video").forEach {
            val encoded = it.selectFirst("a")?.attr("data-player") ?: ""
            val decoded = base64Decode(encoded)
            val url = decoded.replace("https://monoschinos2.com/reproductor?url=", "")
                    .replace("https://sblona.com", "https://watchsb.com")
            loadExtractor(url, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
