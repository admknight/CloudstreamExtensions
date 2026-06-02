package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup
import java.util.*

class DubbedAnimeProvider : MainAPI() {
    override var mainUrl = "https://bestdubbedanime.com"
    override var name = "DubbedAnime"
    override val hasQuickSearch = true
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
    )

    data class QueryEpisodeResultRoot(
        @JsonProperty("result")
        val result: QueryEpisodeResult,
    )

    data class QueryEpisodeResult(
        @JsonProperty("anime") val anime: List<EpisodeInfo>,
        @JsonProperty("error") val error: Boolean,
        @JsonProperty("errorMSG") val errorMSG: String?,
    )

    data class EpisodeInfo(
        @JsonProperty("serversHTML") val serversHTML: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("preview_img") val previewImg: String?,
        @JsonProperty("wideImg") val wideImg: String?,
        @JsonProperty("year") val year: String?,
        @JsonProperty("desc") val desc: String?,
    )

    private suspend fun parseDocumentTrending(url: String): List<SearchResponse> {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        return document.select("li > a").mapNotNull {
            val href = fixUrl(it.attr("href"))
            val title = it.selectFirst("> div > div.cittx")?.text() ?: return@mapNotNull null
            val poster = fixUrlNull(it.selectFirst("> div > div.imghddde > img")?.attr("src"))
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
                addDubStatus(true, null)
            }
        }
    }

    private suspend fun parseDocument(
        url: String,
        trimEpisode: Boolean = false
    ): List<SearchResponse> {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        return document.select("a.grid__link").mapNotNull {
            val href = fixUrl(it.attr("href"))
            val title = it.selectFirst("> div.gridtitlek")?.text() ?: return@mapNotNull null
            val poster = fixUrlNull(it.selectFirst("> img.grid__img")?.attr("src"))
            newAnimeSearchResponse(
                title,
                if (trimEpisode) href.substringBeforeLast('/') else href,
                TvType.Anime
            ) {
                this.posterUrl = poster
                addDubStatus(true, null)
            }
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val trendingUrl = "$mainUrl/xz/trending.php?_=$unixTimeMS"
        val lastEpisodeUrl = "$mainUrl/xz/epgrid.php?p=1&_=$unixTimeMS"
        val recentlyAddedUrl = "$mainUrl/xz/gridgrabrecent.php?p=1&_=$unixTimeMS"

        val listItems = listOf(
            HomePageList("Trending", parseDocumentTrending(trendingUrl)),
            HomePageList("Recently Added", parseDocument(recentlyAddedUrl)),
            HomePageList("Recent Releases", parseDocument(lastEpisodeUrl, true)),
        )

        return newHomePageResponse(listItems)
    }


    private suspend fun getEpisode(slug: String, isMovie: Boolean): EpisodeInfo {
        val url = mainUrl + (if (isMovie) "/movies/jsonMovie" else "/xz/v3/jsonEpi") + ".php?slug=$slug&_=$unixTime"
        val response = app.get(url).text
        val mapped = parseJson<QueryEpisodeResultRoot>(response)
        return mapped.result.anime.first()
    }


    private fun getIsMovie(href: String): Boolean {
        return href.contains("movies/")
    }

    private fun getSlug(href: String): String {
        return href.replace("$mainUrl/", "")
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        val url = "$mainUrl/xz/searchgrid.php?p=1&limit=12&s=$query&_=$unixTime"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("div.grid__item > a")
        if (items.isEmpty()) return emptyList()
        return items.mapNotNull { i ->
            val href = fixUrl(i.attr("href"))
            val title = i.selectFirst("div.gridtitlek")?.text() ?: return@mapNotNull null
            val img = fixUrlNull(i.selectFirst("img.grid__img")?.attr("src"))

            if (getIsMovie(href)) {
                newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                    this.posterUrl = img
                }
            } else {
                newAnimeSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = img
                    addDubStatus(true, null)
                }
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("div.resultinner > a.resulta")
        if (items.isEmpty()) return ArrayList()
        return items.mapNotNull { i ->
            val innerDiv = i.selectFirst("> div.result")
            val href = fixUrl(i.attr("href"))
            val img = fixUrlNull(innerDiv?.selectFirst("> div.imgkz > img")?.attr("src"))
            val title = innerDiv?.selectFirst("> div.titleresults")?.text() ?: return@mapNotNull null

            if (getIsMovie(href)) {
                newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                    this.posterUrl = img
                }
            } else {
                newAnimeSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = img
                    addDubStatus(true, null)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val serversHTML = (if (data.startsWith(mainUrl)) {
            val slug = getSlug(data)
            getEpisode(slug, false).serversHTML
        } else data).replace("\\", "")

        val hls = "hl=\"(.*?)\"".toRegex().findAll(serversHTML).map {
            it.groupValues[1]
        }.toList()
        
        for (hl in hls) {
            try {
                val sources = app.get("$mainUrl/xz/api/playeri.php?url=$hl&_=$unixTime").text
                val find = "src=\"(.*?)\".*?label=\"(.*?)\"".toRegex().find(sources)
                if (find != null) {
                    val quality = find.groupValues[2]
                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            "${this.name} $quality${if (quality.endsWith('p')) "" else 'p'}",
                            fixUrl(find.groupValues[1]),
                            INFER_TYPE
                        ) {
                            this.referer = mainUrl
                            this.quality = getQualityFromName(quality)
                        }
                    )
                }
            } catch (e: Exception) {
            }
        }
        return true
    }

    override suspend fun load(url: String): LoadResponse {
        if (getIsMovie(url)) {
            val realSlug = url.replace("movies/", "")
            val episode = getEpisode(realSlug, true)
            val poster = episode.previewImg ?: episode.wideImg
            
            return newMovieLoadResponse(
                episode.title,
                realSlug,
                TvType.AnimeMovie,
                episode.serversHTML
            ) {
                this.posterUrl = fixUrlNull(poster)
                this.year = episode.year?.toIntOrNull()
                this.plot = episode.desc
            }
        } else {
            val response = app.get(url).text
            val document = Jsoup.parse(response)
            val title = document.selectFirst("h4")?.text() ?: ""
            val descriptHeader = document.selectFirst("div.animeDescript")
            val descript = descriptHeader?.selectFirst("> p")?.text()
            val year = descriptHeader?.selectFirst("> div.distatsx > div.sroverd")
                ?.text()
                ?.replace("Released: ", "")
                ?.toIntOrNull()

            val episodes = document.select("a.epibloks").map {
                val epTitle = it.selectFirst("> div.inwel > span.isgrxx")?.text()
                newEpisode(fixUrl(it.attr("href"))) {
                    this.name = epTitle
                }
            }

            val img = fixUrlNull(document.selectFirst("div.fkimgs > img")?.attr("src"))
            return newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = img
                this.year = year
                addEpisodes(DubStatus.Dubbed, episodes)
                this.plot = descript
            }
        }
    }
}
