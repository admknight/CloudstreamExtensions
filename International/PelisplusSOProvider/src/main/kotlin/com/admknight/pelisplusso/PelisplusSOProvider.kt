package com.admknight.pelisplusso

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup

class PelisplusSOProvider : MainAPI() {
    override var mainUrl = "https://pelisplusgo.vip"
    override var name = "Pelisplus.so"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/series", "Series actualizadas"),
            Pair("$mainUrl/", "Peliculas actualizadas"),
        )
        
        runAllAsync({
            val estrenos = app.get(mainUrl).document.select("div#owl-demo-premiere-movies .pull-left").map {
                val title = it.selectFirst("p")?.text() ?: ""
                newMovieSearchResponse(title, fixUrl(it.selectFirst("a")?.attr("href") ?: ""), TvType.Movie) {
                    this.posterUrl = it.selectFirst("img")?.attr("src")
                    this.year = it.selectFirst("span.year")?.text()?.toIntOrNull()
                }
            }
            items.add(HomePageList("Estrenos", estrenos))

            urls.forEach { (url, name) ->
                val home = app.get(url).document.select(".main-peliculas div.item-pelicula").map {
                    val title = it.selectFirst(".item-detail p")?.text() ?: ""
                    newMovieSearchResponse(title.replace(Regex("(\\d+)x(\\d+)"), ""), fixUrl(it.selectFirst("a")?.attr("href") ?: ""), TvType.Movie) {
                        this.posterUrl = it.selectFirst("img")?.attr("src")
                        this.year = it.selectFirst("span.year")?.text()?.toIntOrNull()
                    }
                }
                items.add(HomePageList(name, home))
            }
        })

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search.html?keyword=${query}"
        val document = app.get(url, headers = mapOf("Referer" to url)).document

        return document.select(".item-pelicula.pull-left").map {
            val title = it.selectFirst("div.item-detail p")?.text() ?: ""
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            val image = it.selectFirst("figure img")?.attr("src")
            val isMovie = href.contains("/pelicula/")

            newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = image
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val title = soup.selectFirst(".info-content h1")?.text() ?: ""
        val description = soup.selectFirst("span.sinopsis")?.text()?.trim()
        val poster = soup.selectFirst(".poster img")?.attr("src")
        
        val episodes = soup.select(".item-season-episodes a").map { li ->
            val href = fixUrl(li.attr("href") ?: "")
            val parts = href.substringAfter("temporada-").replace("/capitulo/", "-").split("-").mapNotNull { it.toIntOrNull() }
            newEpisode(href) {
                this.name = li.text()
                this.season = parts.getOrNull(0)
                this.episode = parts.getOrNull(1)
            }
        }.reversed()

        val year = Regex("(\\d{4})").find(soup.select(".info-half").text())?.value?.toIntOrNull()
        val tvType = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries
        val tags = soup.select(".content-type-a a").map { it.text().trim().replace(", ", "") }
        val duration = Regex("(\\d+)").find(soup.select("p.info-half:nth-child(4)").text())?.value?.toIntOrNull()

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.duration = duration
            }
        }
    }

    private suspend fun getPelisStream(link: String, lang: String? = null, callback: (ExtractorLink) -> Unit) : Boolean {
        val soup = app.get(link).text
        val m3u8 = Regex("((https:|http:)\\/\\/.*m3u8(|.*expiry=(\\d+)))").find(soup)?.value ?: return false

        generateM3u8(name, m3u8, mainUrl).forEach {
            callback(newExtractorLink(name, "$name $lang", it.url, INFER_TYPE) {
                this.quality = getQualityFromName(it.quality.toString())
                this.referer = mainUrl
            })
        }
        return true
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val elements = listOf(
            Pair("Latino", ".server-item-1 li.tab-video"),
            Pair("Subtitulado", ".server-item-0 li.tab-video"),
            Pair("Castellano", ".server-item-2 li.tab-video"),
        )
        elements.forEach { (lang, selector) ->
            doc.select(selector).forEach {
                val url = fixUrl(it.attr("data-video"))
                if (url.contains("pelisplay.io")) {
                    getPelisStream(url, lang, callback)
                    app.get(url).document.select("ul.list-server-items li").forEach { li ->
                        loadExtractor(fixUrl(li.attr("data-video")), mainUrl, subtitleCallback, callback)
                    }
                } else {
                    loadExtractor(url, mainUrl, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}
