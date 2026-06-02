package com.lagradost

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.nicehttp.NiceResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.Cipher.ENCRYPT_MODE
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt
import java.util.Date

class SuperStream : MainAPI() {
    private val timeout = 120L
    override var name = "SuperStream"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
    )

    private enum class ResponseTypes(val value: Int) {
        Series(2),
        Movies(1);

        fun toTvType(): TvType {
            return if (this == Series) TvType.TvSeries else TvType.Movie
        }

        companion object {
            fun getResponseType(value: Int?): ResponseTypes {
                return entries.firstOrNull { it.value == value } ?: Movies
            }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val list = listOf(
            Pair("Featured", "featured"),
            Pair("Movies", "movies"),
            Pair("Series", "tv_series"),
            Pair("Anime", "anime"),
        )
        val home = list.map {
            val url = "https://showbox.shegu.net/api/api_common/v1/home/index?api_key=454a8618e545464545&lang=en&type=${it.second}"
            val response = app.get(url, timeout = timeout).parsedSafe<Response>()
            val items = response?.data?.list?.mapNotNull { item ->
                item.toSearchResponse()
            } ?: emptyList()
            HomePageList(it.first, items)
        }
        return newHomePageResponse(home, false)
    }

    private fun DataList.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(title ?: return null, id.toString(), ResponseTypes.getResponseType(type).toTvType()) {
            this.posterUrl = thumbs ?: thumbsBak ?: thumbsMin ?: thumbsOriginal ?: thumbsOrg
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://showbox.shegu.net/api/api_common/v1/search/index?api_key=454a8618e545464545&lang=en&keyword=$query"
        val response = app.get(url, timeout = timeout).parsedSafe<Response>()
        return response?.data?.list?.mapNotNull { item ->
            item.toSearchResponse()
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.toIntOrNull() ?: return null
        val detailUrl = "https://showbox.shegu.net/api/api_common/v1/parent/detail?api_key=454a8618e545464545&lang=en&id=$id"
        val response = app.get(detailUrl, timeout = timeout).parsedSafe<Response>()
        val data = response?.data ?: return null

        val title = data.title ?: data.name ?: return null
        val poster = data.thumbs ?: data.thumbsBak ?: data.thumbsMin ?: data.thumbsOriginal ?: data.thumbsOrg
        val plot = data.synopsis
        val year = data.year
        val rating = data.imdbRating?.toDoubleOrNull()?.times(10)?.roundToInt()
        val trailer = data.trailerUrl

        if (data.type == ResponseTypes.Series.value) {
            val episodesUrl = "https://showbox.shegu.net/api/api_common/v1/episode/list?api_key=454a8618e545464545&lang=en&id=$id"
            val episodesResponse = app.get(episodesUrl, timeout = timeout).parsedSafe<Response>()
            val episodes = episodesResponse?.data?.list ?: emptyList()
            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes.mapNotNull {
                    newEpisode(
                        LinkData(
                            it.tid ?: it.id ?: return@mapNotNull null,
                            ResponseTypes.Series.value,
                            it.season,
                            it.episode
                        ).toJson()
                    ) {
                        this.name = it.title
                        this.season = it.season
                        this.episode = it.episode
                        this.posterUrl = it.thumbs ?: it.thumbsBak ?: it.thumbsMin ?: it.thumbsOriginal ?: it.thumbsOrg
                        this.score = Score.from10(it.imdbRating?.toDoubleOrNull()?.times(10)?.roundToInt())
                        this.description = it.synopsis
                        addDate(it.releasedTimestamp?.let { ts -> Date(ts * 1000) })
                    }
                }
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.score = Score.from10(rating)
                addTrailer(trailer)
                addImdbId(data.imdbId)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(id, ResponseTypes.Movies.value).toJson()
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.score = Score.from10(rating)
                addTrailer(trailer)
                addImdbId(data.imdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)
        val url = "https://showbox.shegu.net/api/api_common/v1/parent/links?api_key=454a8618e545464545&lang=en&id=${linkData.id}&type=${linkData.type}${if (linkData.season != null) "&season=${linkData.season}&episode=${linkData.episode}" else ""}"
        val response = app.get(url, timeout = timeout).parsedSafe<Response>()
        val links = response?.data?.list ?: emptyList()

        links.forEach {
            callback.invoke(
                newExtractorLink(
                    source = it.source ?: name,
                    name = it.source ?: name,
                    url = it.url ?: return@forEach,
                    type = INFER_TYPE
                ) {
                    this.referer = it.referer ?: ""
                    this.quality = getQualityFromName(it.quality)
                }
            )
        }
        return true
    }

    data class LinkData(
        val id: Int,
        val type: Int,
        val season: Int? = null,
        val episode: Int? = null
    )

    data class Response(
        @JsonProperty("data") val data: Data? = null,
    )

    data class Data(
        @JsonProperty("list") val list: List<DataList>? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("thumbs") val thumbs: String? = null,
        @JsonProperty("thumbs_bak") val thumbsBak: String? = null,
        @JsonProperty("thumbs_min") val thumbsMin: String? = null,
        @JsonProperty("thumbs_original") val thumbsOriginal: String? = null,
        @JsonProperty("thumbs_org") val thumbsOrg: String? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("trailer_url") val trailerUrl: String? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("type") val type: Int? = null,
    )

    data class DataList(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("tid") val tid: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("thumbs") val thumbs: String? = null,
        @JsonProperty("thumbs_bak") val thumbsBak: String? = null,
        @JsonProperty("thumbs_min") val thumbsMin: String? = null,
        @JsonProperty("thumbs_original") val thumbsOriginal: String? = null,
        @JsonProperty("thumbs_org") val thumbsOrg: String? = null,
        @JsonProperty("type") val type: Int? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("released_timestamp") val releasedTimestamp: Long? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("referer") val referer: String? = null,
        @JsonProperty("quality") val quality: String? = null,
    )

    object CipherUtils {
        fun getVerify(str: String, str2: String, str3: String): String {
            return md5(str + str2 + str3)
        }

        private fun md5(str: String): String {
            try {
                val digest = MessageDigest.getInstance("MD5")
                digest.update(str.toByteArray())
                val messageDigest = digest.digest()
                val hexString = StringBuilder()
                for (aMessageDigest in messageDigest) {
                    var h = Integer.toHexString(0xFF and aMessageDigest.toInt())
                    while (h.length < 2) h = "0$h"
                    hexString.append(h)
                }
                return hexString.toString()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
            return ""
        }
    }
}
