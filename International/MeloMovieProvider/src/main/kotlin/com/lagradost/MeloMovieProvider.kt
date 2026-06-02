package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class MeloMovieProvider : MainAPI() {
    override var name = "MeloMovie"
    override var mainUrl = "https://melomovie.com"
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val hasChromecastSupport = false

    data class MeloMovieSearchResult(
        @JsonProperty("id") val id: Int,
        @JsonProperty("imdb_code") val imdbId: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: Int,
        @JsonProperty("year") val year: Int?,
    )

    data class MeloMovieLink(
        @JsonProperty("name") val name: String,
        @JsonProperty("link") val link: String
    )

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/movie/search/?name=$query"
        val returnValue = ArrayList<SearchResponse>()
        val response = app.get(url).text
        val mapped = runCatching { parseJson<List<MeloMovieSearchResult>>(response) }.getOrNull() ?: emptyList()

        mapped.forEach { i ->
            val currentUrl = "$mainUrl/movie/${i.id}"
            val currentPoster = "$mainUrl/assets/images/poster/${i.imdbId}.jpg"
            if (i.type == 2) {
                returnValue.add(newTvSeriesSearchResponse(i.title, currentUrl, TvType.TvSeries) {
                    this.posterUrl = currentPoster
                    this.year = i.year
                })
            } else if (i.type == 1) {
                returnValue.add(newMovieSearchResponse(i.title, currentUrl, TvType.Movie) {
                    this.posterUrl = currentPoster
                    this.year = i.year
                })
            }
        }
        return returnValue
    }

    private fun fixMeloUrl(url: String): String {
        if (url.isEmpty()) return ""
        if (url.startsWith("//")) return "http:$url"
        if (!url.startsWith("http")) return "http://$url"
        return url
    }

    private fun serializeData(element: Element): List<MeloMovieLink> {
        val eps = element.select("> tbody > tr")
        return eps.mapNotNull {
            runCatching {
                val tds = it.select("> td")
                val mName = tds[if (tds.size == 5) 1 else 0].text()
                val mUrl = fixMeloUrl(tds.last()?.selectFirst("> a")?.attr("data-lnk")?.replace(" ", "%20") ?: "")
                if (mName.isNotEmpty() && mUrl.isNotEmpty()) MeloMovieLink(mName, mUrl) else null
            }.getOrNull()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = try { parseJson<List<MeloMovieLink>>(data) } catch (_: Exception) { emptyList() }
        links.forEach { link ->
            callback.invoke(newExtractorLink(this.name, link.name, link.link, INFER_TYPE) {
                this.quality = getQualityFromName(link.name)
            })
        }
        return links.isNotEmpty()
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val imdbUrl = "var imdb = \"(.*?)\"".toRegex().find(response)?.groupValues?.get(1)
        val type = "var posttype = ([0-9]*)".toRegex().find(response)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        
        val document = Jsoup.parse(response)
        val poster = document.selectFirst("img.img-fluid")?.attr("src")
        val titleInfo = document.selectFirst("div.movie_detail_title > div > div > h1")
        val title = titleInfo?.ownText() ?: ""
        val year = titleInfo?.selectFirst("> a")?.text()?.replace("(", "")?.replace(")", "")?.toIntOrNull()
        val plotStr = document.selectFirst("div.col-lg-12 > p")?.text() ?: ""

        if (type == 1) {
            val serialize = document.selectFirst("table.accordion__list") ?: throw ErrorLoadingException("No links found")
            return newMovieLoadResponse(title, url, TvType.Movie, serializeData(serialize)) {
                this.posterUrl = poster
                this.year = year
                this.plot = plotStr
                addImdbUrl(imdbUrl)
            }
        } else if (type == 2) {
            val episodes = ArrayList<Episode>()
            val seasons = document.select("div.accordion__card")
            seasons.forEach { s ->
                val seasonNum = s.selectFirst("> div.card-header > button > span")?.text()?.replace("Season: ", "")?.toIntOrNull()
                val localEpisodes = s.select("> div.collapse > div > div > div.accordion__card")
                localEpisodes.forEach { e ->
                    val episodeNum = e.selectFirst("> div.card-header > button > span")?.text()?.replace("Episode: ", "")?.toIntOrNull()
                    val links = e.selectFirst("> div.collapse > div > table.accordion__list") ?: return@forEach
                    val epData = serializeData(links)
                    episodes.add(newEpisode(epData) {
                        this.season = seasonNum
                        this.episode = episodeNum
                    })
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
                this.posterUrl = poster
                this.year = year
                this.plot = plotStr
                addImdbUrl(imdbUrl)
            }
        }
        return null
    }
}
