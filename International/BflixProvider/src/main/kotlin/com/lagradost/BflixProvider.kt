package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.NineAnimeApi.decodeVrf
import com.lagradost.NineAnimeApi.encode
import com.lagradost.NineAnimeApi.encodeVrf
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup

open class BflixProvider : MainAPI() {
    override var mainUrl = "https://bflix.ru"
    override var name = "Bflix"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get("$mainUrl/home").document
        val testa = listOf(
            Pair("Movies", "div.tab-content[data-name=movies] div.filmlist div.item"),
            Pair("Shows", "div.tab-content[data-name=shows] div.filmlist div.item"),
            Pair("Trending", "div.tab-content[data-name=trending] div.filmlist div.item"),
            Pair("Latest Movies", "div.container section.bl:contains(Latest Movies) div.filmlist div.item"),
            Pair("Latest TV-Series", "div.container section.bl:contains(Latest TV-Series) div.filmlist div.item"),
        )
        for ((name, element) in testa) try {
            val results = soup.select(element).map {
                val title = it.selectFirst("h3 a")?.text() ?: ""
                val link = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
                val poster = it.selectFirst("a.poster img")?.attr("src")
                val isMovie = link.contains("/movie/")
                
                newMovieSearchResponse(title, link, if (isMovie) TvType.Movie else TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
            items.add(HomePageList(name, results))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedquery = encodeVrf(query, mainKey)
        val url = "$mainUrl/search?keyword=$query&vrf=$encodedquery"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select(".filmlist div.item").map {
            val title = it.selectFirst("h3 a")?.text() ?: ""
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            val image = it.selectFirst("a.poster img")?.attr("src")
            val isMovie = href.contains("/movie/")

            newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = image
            }
        }
    }

    data class Response(
        @JsonProperty("html") val html: String
    )

    companion object {
        val mainKey = "OrAimkpzm6phmN3j"
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val movieid = soup.selectFirst("div#watch")?.attr("data-id") ?: return null
        val movieidencoded = encodeVrf(movieid, mainKey)
        val title = soup.selectFirst("div.info h1")?.text() ?: ""
        val description = soup.selectFirst(".info .desc")?.text()?.trim()
        val poster = soup.selectFirst("img.poster")?.attr("src") ?: soup.selectFirst(".info .poster img")?.attr("src")

        val tags = soup.select("div.info .meta div:contains(Genre) a").map { it.text() }
        val vrfUrl = "$mainUrl/ajax/film/servers?id=$movieid&vrf=$movieidencoded"
        
        val episodes = Jsoup.parse(
            app.get(vrfUrl).parsed<Response>().html
        ).select("div.episode").map {
            val a = it.selectFirst("a") ?: return@map null
            val href = fixUrl(a.attr("href"))
            val extraData = a.attr("data-kname").split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
            val isValid = extraData.size == 2
            val epNum = if (isValid) extraData.getOrNull(1) else null
            val sNum = if (isValid) extraData.getOrNull(0) else null

            val eptitle = it.selectFirst("span.name")?.text() ?: ""
            val secondtitle = it.selectFirst("span")?.text()?.replace(Regex("(Episode (\\d+):|Episode (\\d+)-|Episode (\\d+))"), "")?.trim() ?: ""
            
            newEpisode(href) {
                this.name = if (secondtitle.isNotBlank()) "$secondtitle $eptitle" else eptitle
                this.season = sNum
                this.episode = epNum
            }
        }.filterNotNull()

        val tvType = if (url.contains("/movie/") && episodes.size == 1) TvType.Movie else TvType.TvSeries
        
        val recommendations = soup.select("div.bl-2 section.bl div.content div.filmlist div.item")
                .mapNotNull { element ->
                    val recTitle = element.select("h3 a").text() ?: return@mapNotNull null
                    val image = element.select("a.poster img").attr("src")
                    val recUrl = fixUrl(element.select("a").attr("href"))
                    newMovieSearchResponse(recTitle, recUrl, if (recUrl.contains("/movie/")) TvType.Movie else TvType.TvSeries) {
                        this.posterUrl = image
                    }
                }
        
        val scoreValue = Score.from10(soup.selectFirst(".info span.imdb")?.text()?.substringAfter("IMDb:")?.trim())
        val durationdoc = soup.selectFirst("div.info div.meta")?.text() ?: ""
        val duration = Regex("(\\d+) min").find(durationdoc)?.groupValues?.get(1)?.toIntOrNull()
        val year = Regex("(\\d{4})").find(durationdoc)?.groupValues?.get(1)?.toIntOrNull()

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = scoreValue
                this.tags = tags
                this.recommendations = recommendations
                this.duration = duration
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = scoreValue
                this.tags = tags
                this.recommendations = recommendations
                this.duration = duration
            }
        }
    }

    data class Subtitles(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("kind") val kind: String
    )

    data class Links(
        @JsonProperty("url") val url: String
    )

    data class Servers(
        @JsonProperty("28") val mcloud: String?,
        @JsonProperty("35") val mp4upload: String?,
        @JsonProperty("40") val streamtape: String?,
        @JsonProperty("41") val vidstream: String?,
        @JsonProperty("43") val videovard: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val soup = app.get(data).document

        val movieid = soup.selectFirst("div#watch")?.attr("data-id") ?: return false
        val movieidencoded = encodeVrf(movieid, mainKey)
        
        val html = app.get("$mainUrl/ajax/film/servers?id=$movieid&vrf=$movieidencoded").parsed<Response>().html
        val serverDoc = Jsoup.parse(html)
        
        serverDoc.select(".episode a").forEach { server ->
            val epId = server.attr("data-ep")
            val epserver = app.get("$mainUrl/ajax/episode/info?id=$epId").text
            val link = parseJson<Links>(epserver).url
            val url = decodeVrf(link, mainKey)
            
            loadExtractor(url, data, subtitleCallback, callback)
            
            // Try to get subtitles
            runCatching {
                val sublink = app.get("$mainUrl/ajax/episode/subtitles/$epId").text
                val jsonsub = parseJson<List<Subtitles>>(sublink)
                jsonsub.forEach { subtitle ->
                    subtitleCallback(SubtitleFile(subtitle.label, subtitle.file))
                }
            }
        }

        return true
    }
}
