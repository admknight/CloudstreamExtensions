package com.stormunblessed

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import java.util.*

class AnimeflvIOProvider : MainAPI() {
    override var mainUrl = "https://animeflv.io"
    override var name = "Animeflv.io"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.OVA, TvType.Anime)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val estrenos = app.get(mainUrl).document.select("div#owl-demo-premiere-movies .pull-left").map {
            val title = it.selectFirst("p")?.text() ?: ""
            newAnimeSearchResponse(title, fixUrl(it.selectFirst("a")?.attr("href") ?: ""), TvType.Anime) {
                this.posterUrl = it.selectFirst("img")?.attr("src")
                addDubStatus(DubStatus.Subbed)
            }
        }
        items.add(HomePageList("Estrenos", estrenos))

        val urls = listOf(Pair("$mainUrl/series", "Series actualizadas"), Pair("$mainUrl/peliculas", "Peliculas actualizadas"))
        urls.forEach { (url, name) ->
            val results = app.get(url).document.select("div.item-pelicula").map {
                val title = it.selectFirst(".item-detail p")?.text() ?: ""
                val poster = it.selectFirst("figure img")?.attr("src")
                newAnimeSearchResponse(title, fixUrl(it.selectFirst("a")?.attr("href") ?: ""), TvType.Anime) {
                    this.posterUrl = poster
                    addDubStatus(if (title.contains("Latino") || title.contains("Castellano")) DubStatus.Dubbed else DubStatus.Subbed)
                }
            }
            items.add(HomePageList(name, results))
        }
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search.html?keyword=$query", headers = mapOf("Referer" to mainUrl)).document
        return doc.select(".item-pelicula.pull-left").map {
            val title = it.selectFirst("div.item-detail p")?.text() ?: ""
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            val poster = it.selectFirst("figure img")?.attr("src")
            val isMovie = href.contains("/pelicula/")
            
            newAnimeSearchResponse(title, href, if (isMovie) TvType.AnimeMovie else TvType.Anime) {
                this.posterUrl = poster
                addDubStatus(DubStatus.Subbed)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst(".info-content h1")?.text() ?: ""
        val description = doc.selectFirst("span.sinopsis")?.text()?.trim()
        val poster = doc.selectFirst(".poster img")?.attr("src")
        
        val episodes = doc.select(".item-season-episodes a").map { li ->
            val href = fixUrl(li.attr("href") ?: "")
            newEpisode(href) { this.name = li.text() }
        }.reversed()

        val yearVal = Regex("(\\d+)").find(doc.select(".info-half").text())?.value?.toIntOrNull()
        val tvType = if (url.contains("/pelicula/")) TvType.AnimeMovie else TvType.Anime
        val tags = doc.select(".content-type-a a").map { it.text().trim().replace(", ", "") }
        val duration = Regex("(\\d+)").find(doc.select("p.info-half:nth-child(4)").text())?.value?.toIntOrNull()

        return if (tvType == TvType.Anime) {
            newAnimeLoadResponse(title, url, tvType) {
                this.posterUrl = poster
                this.year = yearVal
                addEpisodes(DubStatus.Subbed, episodes)
                this.plot = description
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = poster
                this.year = yearVal
                this.plot = description
                this.tags = tags
                this.duration = duration
            }
        }
    }

    data class MainJson (@JsonProperty("source") val source: List<Source>)
    data class Source (@JsonProperty("file") val file: String, @JsonProperty("label") val label: String)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("li.tab-video").forEach {
            val vUrl = fixUrl(it.attr("data-video"))
            if (vUrl.contains("animeid")) {
                val json = try { app.get(vUrl.replace("streaming.php", "ajax.php")).parsed<MainJson>() } catch (_: Exception) { null }
                json?.source?.forEach { src ->
                    if (src.file.contains("m3u8")) {
                        generateM3u8("Animeflv.io", src.file, "https://animeid.to", headers = mapOf("Referer" to "https://animeid.to")).forEach(callback)
                    } else {
                        callback(newExtractorLink(name, "$name ${src.label}", src.file, INFER_TYPE) {
                            this.quality = Qualities.Unknown.value
                            this.referer = "https://animeid.to"
                        })
                    }
                }
            }
            loadExtractor(vUrl, data, subtitleCallback, callback)
        }
        return true
    }
}
