package com.admknight.stremio

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URLEncoder

class StremioProvider : MainAPI() {
    override var mainUrl = "https://v3-cinemeta.strem.io"
    override var name = "Stremio"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/catalog/movie/top/skip/${(page - 1) * 20}.json", "Top Movies"),
            Pair("$mainUrl/catalog/series/top/skip/${(page - 1) * 20}.json", "Top Series"),
        )

        val items = ArrayList<HomePageList>()
        for (i in urls) {
            val response = app.get(i.first).text
            val mapped = parseJson<StremioResponse>(response)
            val results = mapped.metas.map {
                newMovieSearchResponse(it.name, it.id, if (it.type == "movie") TvType.Movie else TvType.TvSeries) {
                    this.posterUrl = it.poster
                }
            }
            items.add(HomePageList(i.second, results))
        }

        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/catalog/movie/top/search=${URLEncoder.encode(query, "utf-8")}.json"
        val response = app.get(url).text
        val mapped = parseJson<StremioResponse>(response)
        val movies = mapped.metas.map {
            newMovieSearchResponse(it.name, it.id, TvType.Movie) {
                this.posterUrl = it.poster
            }
        }

        val urlSeries = "$mainUrl/catalog/series/top/search=${URLEncoder.encode(query, "utf-8")}.json"
        val responseSeries = app.get(urlSeries).text
        val mappedSeries = parseJson<StremioResponse>(responseSeries)
        val series = mappedSeries.metas.map {
            newTvSeriesSearchResponse(it.name, it.id, TvType.TvSeries) {
                this.posterUrl = it.poster
            }
        }

        return movies + series
    }

    override suspend fun load(url: String): LoadResponse {
        val type = if (url.startsWith("tt")) {
            val checkUrl = "$mainUrl/meta/movie/$url.json"
            val checkResponse = app.get(checkUrl).text
            if (checkResponse.contains("\"meta\"")) "movie" else "series"
        } else "series"

        val metaUrl = "$mainUrl/meta/$type/$url.json"
        val response = app.get(metaUrl).text
        val mapped = parseJson<StremioMetaResponse>(response).meta

        return if (type == "movie") {
            newMovieLoadResponse(mapped.name, url, TvType.Movie, url) {
                this.posterUrl = mapped.poster
                this.plot = mapped.description
                this.year = mapped.year?.toIntOrNull()
            }
        } else {
            val episodes = mapped.videos?.map {
                newEpisode(it.id) {
                    this.name = it.title
                    this.season = it.season
                    this.episode = it.number
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(mapped.name, url, TvType.TvSeries, episodes) {
                this.posterUrl = mapped.poster
                this.plot = mapped.description
                this.year = mapped.year?.toIntOrNull()
            }
        }
    }

    data class StremioResponse(
        @JsonProperty("metas") val metas: List<Meta>
    )

    data class Meta(
        @JsonProperty("id") val id: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("poster") val poster: String?
    )

    data class StremioMetaResponse(
        @JsonProperty("meta") val meta: MetaDetails
    )

    data class MetaDetails(
        @JsonProperty("id") val id: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("year") val year: String?,
        @JsonProperty("videos") val videos: List<Video>?
    )

    data class Video(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("number") val number: Int?
    )
}
