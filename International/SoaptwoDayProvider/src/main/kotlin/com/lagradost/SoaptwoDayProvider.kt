package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup

class SoaptwoDayProvider : MainAPI() {
    override var mainUrl = "https://secretlink.xyz"
    override var name = "Soap2Day"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/movielist?page=" to "Movies",
        "$mainUrl/tvlist?page=" to "TV Series",
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val url = request.data + page
        val soup = app.get(url).document
        val home = soup.select("div.col-xs-6").mapNotNull {
            val title = it.selectFirst("h5 a")?.text() ?: ""
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            newMovieSearchResponse(title, link, TvType.TvSeries) {
                this.posterUrl = fixUrl(it.selectFirst("img")?.attr("src") ?: "")
            }
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search/keyword/$query").document
        return doc.select("div.col-xs-6").mapNotNull {
            val title = it.selectFirst("h5 a")?.text() ?: ""
            val image = fixUrl(it.selectFirst("img")?.attr("src") ?: "")
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = image
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val title = soup.selectFirst(".hidden-lg > div:nth-child(1) > h4")?.text() ?: ""
        val description = soup.selectFirst("p#wrap")?.text()?.trim()
        val poster = soup.selectFirst(".col-md-5 > div:nth-child(1) > div:nth-child(1) > img")?.attr("src")
        
        val episodes = mutableListOf<Episode>()
        soup.select("div.alert").forEach { alert ->
            val seasonNum = alert.selectFirst("h4")?.text()?.filter { it.isDigit() }?.toIntOrNull()
            alert.select("div > div > a").forEach { entry ->
                val link = fixUrlNull(entry.attr("href")) ?: return@forEach
                val text = entry.text() ?: ""
                val name = text.replace(Regex("(^(\\d+)\\.)"), "").trim()
                val epNum = text.substringBefore(".", "").toIntOrNull()
                episodes.add(newEpisode(link) {
                    this.name = name
                    this.season = seasonNum
                    this.episode = epNum
                })
            }
        }

        val otherInfo = soup.selectFirst("div.col-sm-8 div.panel-body")?.toString() ?: ""
        val casts = Jsoup.parse(otherInfo.substringAfter("Stars : ").substringBefore("Genre : ")).select("a").map {
            ActorData(Actor(it.text().trim()))
        }
        val year = Jsoup.parse(otherInfo.substringAfter("<h4>Release : </h4>").substringBefore("<div")).select("p").getOrNull(1)?.text()?.take(4)?.toIntOrNull()
        val genres = Jsoup.parse(otherInfo.substringAfter("<h4>Genre : </h4>").substringBefore("<h4>Release : </h4>")).select("a").map { it.text().trim() }

        val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries
        
        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, tvType, episodes.reversed()) {
                this.posterUrl = fixUrlNull(poster)
                this.year = year
                this.plot = description
                this.actors = casts
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = fixUrlNull(poster)
                this.year = year
                this.plot = description
                this.actors = casts
                this.tags = genres
            }
        }
    }

    data class ServerJson(
        @JsonProperty("val") val stream: String?,
        @JsonProperty("val_bak") val streambackup: String?,
        @JsonProperty("subs") val subs: List<Subs>?
    )

    data class Subs(
        @JsonProperty("name") val name: String,
        @JsonProperty("path") val path: String?,
        @JsonProperty("downlink") val downlink: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val id1 = doc.selectFirst("#divU")?.text()
        val id2 = doc.selectFirst("#divP")?.text()
        val movieId = doc.selectFirst("div.row input#hId")?.attr("value") ?: return false
        val isMovie = doc.selectFirst(".col-md-5 > div:nth-child(1) > div:nth-child(1) > img")?.attr("src")?.contains("movie") == true
        val ajaxLink = if (isMovie) "$mainUrl/home/index/GetMInfoAjax" else "$mainUrl/home/index/GetEInfoAjax"
        
        listOf(id1, id2).forEach { playerID ->
            if (playerID.isNullOrBlank()) return@forEach
            val responseText = app.post(
                ajaxLink,
                headers = mapOf("Referer" to data, "X-Requested-With" to "XMLHttpRequest"),
                data = mapOf("pass" to movieId, "param" to playerID)
            ).text.replace("\\\"", "\"").replace("\"{", "{").replace("}\"", "}").replace("\\\\\\/", "/")
            
            val json = try { parseJson<ServerJson>(responseText) } catch (_: Exception) { null }
            listOfNotNull(json?.stream, json?.streambackup).forEach { stream ->
                val cleanUrl = stream.replace("\\/", "/").replace("\\\\\\", "")
                if (cleanUrl.isNotBlank()) {
                    callback(newExtractorLink("Soap2Day", "Soap2Day", cleanUrl, INFER_TYPE) {
                        this.referer = "https://soap2day.ac"
                        this.quality = Qualities.Unknown.value
                    })
                }
            }
            json?.subs?.forEach { sub ->
                if (!sub.path.isNullOrBlank()) subtitleCallback(SubtitleFile(sub.name, mainUrl + sub.path))
                if (!sub.downlink.isNullOrBlank()) subtitleCallback(SubtitleFile(sub.name, sub.downlink))
            }
        }
        return true
    }
}
