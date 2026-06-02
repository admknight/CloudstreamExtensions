package com.admknight.monoschinos

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*

class MonoschinosProvider : MainAPI() {
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

        var latestCookie: Map<String, String> = emptyMap()
        var latestToken = ""
    }

    override var mainUrl = "https://monoschinos2.com"
    override var name = "Monoschinos"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.OVA, TvType.Anime)

    private suspend fun getToken(url: String): Map<String, String> {
        val response = app.get(url)
        val token = response.document.selectFirst("html head meta[name=csrf-token]")?.attr("content") ?: ""
        latestToken = token
        latestCookie = response.cookies
        return latestCookie
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
                Pair("$mainUrl/emision", "Estrenos 2024"),
                Pair("$mainUrl/animes", "Animes"),
        )
        val items = ArrayList<HomePageList>()
        
        val updated = app.get(mainUrl, timeout = 120).document.select(".row-cols-xl-4 li article").mapNotNull {
            val title = it.selectFirst("h2")?.text() ?: it.selectFirst("h2.text-truncate")?.text() ?: ""
            val poster = it.selectFirst("img")?.attr("data-src") ?: ""
            val epRegex = Regex("episodio-(\\d+)")
            val url = it.selectFirst("a")?.attr("href")?.replace("ver/", "anime/")?.replace(epRegex, "sub-espanol") ?: return@mapNotNull null
            val epNum = (it.selectFirst("article span.episode")?.text() ?: it.selectFirst("div.positioning p")?.text())?.toIntOrNull()
            
            newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = fixUrl(poster)
                addDubStatus(getDubStatus(title), epNum)
            }
        }
        items.add(HomePageList("Capítulos actualizados", updated, true))

        urls.forEach { (url, name) ->
            val home = app.get(url, timeout = 120).document.select("li.col").mapNotNull {
                val title = it.selectFirst("h3")?.text() ?: ""
                val poster = it.selectFirst("img")?.attr("data-src") ?: ""
                val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                newAnimeSearchResponse(title, fixUrl(link), TvType.Anime) {
                    this.posterUrl = fixUrl(poster)
                    addDubStatus(getDubStatus(title))
                }
            }
            items.add(HomePageList(name, home))
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/buscar?q=$query", timeout = 120).document.select("li.col article").mapNotNull {
            val title = it.selectFirst("h3")?.text() ?: ""
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val image = it.selectFirst("img")?.attr("data-src") ?: ""
            
            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(image)
                addDubStatus(getDubStatus(title))
            }
        }
    }

    data class CapList(@JsonProperty("eps") val eps: List<Ep>)
    data class Ep(val num: Int?)

    override suspend fun load(url: String): LoadResponse {
        getToken(url)
        val doc = app.get(url, timeout = 120).document
        val capListUrl = doc.selectFirst(".caplist")?.attr("data-ajax") ?: throw ErrorLoadingException("Intenta de nuevo")
        val poster = doc.selectFirst("img.w-100")?.attr("data-src") ?: ""
        val backImage = doc.selectFirst("img.rounded-3")?.attr("data-src") ?: ""
        val title = doc.selectFirst(".fs-2")?.text() ?: ""
        val typeStr = doc.selectFirst("div.bg-transparent > dl:nth-child(1) > dd:nth-child(2)")?.text() ?: ""
        val description = doc.selectFirst("div.mb-3")?.text()?.replace("Ver menos", "") ?: ""
        val genres = doc.select(".my-4 > div a span").map { it.text() }
        val status = when (doc.selectFirst("div.col:nth-child(1) > div:nth-child(1) > div")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        
        val capJson = app.post(capListUrl,
                headers = mapOf(
                        "Referer" to url,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to mainUrl
                ),
                cookies = latestCookie,
               data = mapOf("_token" to latestToken)).parsed<CapList>()

        val epList = capJson.eps.map { ep ->
            val epUrl = "${url.replace("-sub-espanol","").replace("/anime/","/ver/")}-episodio-${ep.num}"
            newEpisode(epUrl) {
                this.episode = ep.num
            }
        }

        return newAnimeLoadResponse(title, url, getType(typeStr)) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backImage
            addEpisodes(DubStatus.Subbed, epList)
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
        app.get(data).document.select("#myTab li .play-video").forEach {
            val encodedUrl = it.attr("data-player")
            val decoded = base64Decode(encodedUrl)
            val url = decoded.replace("https://monoschinos2.com/reproductor?url=", "")
                    .replace("https://sblona.com", "https://watchsb.com")
                    .replace("https://swdyu.com", "https://streamwish.to")
            loadExtractor(url, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
