package com.admknight.superstream

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.NiceResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class SuperStreamLegacy : MainAPI() {
    override var name = "SuperStream (Legacy)"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    enum class ResponseTypes(val value: Int) {
        Series(2),
        Movies(1);

        companion object {
            fun getResponseType(value: Int?): ResponseTypes {
                return if (value == 1) Movies else Series
            }
        }
    }

    override val instantLinkLoading = true

    object CipherUtils {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        fun encrypt(data: String, key: String, iv: String): String? {
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val keySpec = SecretKeySpec(key.toByteArray(), ALGORITHM)
                val ivSpec = IvParameterSpec(iv.toByteArray())
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                val encrypted = cipher.doFinal(data.toByteArray())
                Base64.encodeToString(encrypted, Base64.DEFAULT)
            } catch (e: Exception) {
                null
            }
        }

        fun decrypt(data: String, key: String, iv: String): String? {
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val keySpec = SecretKeySpec(key.toByteArray(), ALGORITHM)
                val ivSpec = IvParameterSpec(iv.toByteArray())
                cipher.init(DECRYPT_MODE, keySpec, ivSpec)
                val decodedData = Base64.decode(data, Base64.DEFAULT)
                val decrypted = cipher.doFinal(decodedData)
                String(decrypted)
            } catch (e: Exception) {
                null
            }
        }

        fun md5(str: String): String? {
            return try {
                val md = MessageDigest.getInstance("MD5")
                md.update(str.toByteArray())
                val byteData = md.digest()
                val sb = StringBuilder()
                for (i in byteData.indices) {
                    sb.append(
                        ((byteData[i].toInt() and 0xff) + 0x100).toString(16).substring(1)
                    )
                }
                sb.toString()
            } catch (e: NoSuchAlgorithmException) {
                null
            }
        }

        fun getVerify(str: String?, str2: String, str3: String): String? {
            if (str == null) return null
            val md5 = md5(str)
            return md5(md5 + str2 + str3)
        }
    }

    private suspend fun queryApi(query: String, useSecond: Boolean = false): NiceResponse {
        val currentUrl = if (useSecond) secondApiUrl else apiUrl
        val expire = getExpiryDate()
        val verify = CipherUtils.getVerify(query, expire.toString(), key)
        val url =
            "$currentUrl/api/api_v2/index?app_id=$appId&app_key=$appKey&app_version=$appVersion&app_version_code=$appVersionCode&expire=$expire&platform=android&token=$token&verify=$verify"
        return app.post(
            url,
            data = mapOf("data" to (CipherUtils.encrypt(query, key, iv) ?: ""))
        )
    }

    private suspend inline fun <reified T : Any> queryApiParsed(query: String, useSecond: Boolean = false): T? {
        val response = queryApi(query, useSecond).text
        return try { parseJson<T>(response) } catch(_: Exception) { null }
    }

    private fun getExpiryDate(): Long {
        return (unixTime + 60 * 60 * 1)
    }

    data class PostJSON(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("poster2") val poster2: String?,
        @JsonProperty("box_type") val boxType: Int?,
        @JsonProperty("imdb_rating") val imdbRating: String?,
        @JsonProperty("quality_tag") val quality_tag: String?,
    )

    data class ListJSON(
        @JsonProperty("code") val code: Int?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("box_type") val boxType: Int?,
        @JsonProperty("list") val list: ArrayList<PostJSON>,
    )

    data class DataJSON(
        @JsonProperty("data") val data: ArrayList<ListJSON>,
    )

    private val iv = "p@_u#st7_6977_6^"
    private val key = "123!@#asdfGJK_#!"

    private val ip = "https://156.234.234.166"
    private val apiUrl = ip

    private val secondApiUrl = "https://show.metshow.com"

    private val appKey = "80f4f9f65053229c"
    private val appId = "com.clipper.movie"
    private val appIdSecond = "com.all.video.unlimit"
    private val appVersion = "1.2.6"
    private val appVersionCode = "12"

    private fun randomToken(): String {
        return (0..31).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
    }
    private val token = randomToken()

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val query = "{\"childmode\":\"0\",\"app_id\":\"$appIdSecond\"}"
        val data = queryApiParsed<DataJSON>(query) ?: return null
        val homePageLists = data.data.map {
            HomePageList(
                it.name ?: "Trending",
                it.list.map { post ->
                    newMovieSearchResponse(
                        post.title ?: "",
                        "{\"id\":${post.id},\"type\":${post.boxType}}"
                    ) {
                        this.posterUrl = post.poster ?: post.poster2
                    }
                }
            )
        }
        return newHomePageResponse(homePageLists)
    }

    data class Data(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("mid") val mid: Int?,
        @JsonProperty("box_type") val boxType: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("poster_org") val poster_org: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("cats") val cats: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("imdb_rating") val imdb_rating: String?,
        @JsonProperty("quality_tag") val quality_tag: String?,
    ) {
        fun toSearchResponse(api: MainAPI): MovieSearchResponse? {
            return api.newMovieSearchResponse(
                title ?: return null,
                "{\"id\":$id,\"type\":$boxType}"
            ) {
                this.posterUrl = poster_org ?: poster
            }
        }
    }

    data class MainData(
        @JsonProperty("data") val data: ArrayList<Data>,
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val apiQuery =
            "{\"app_id\":\"$appIdSecond\",\"keyword\":\"$query\",\"page\":\"1\",\"pagelimit\":\"20\"}"
        val data = queryApiParsed<MainData>(apiQuery) ?: return emptyList()
        return data.data.mapNotNull {
            it.toSearchResponse(this)
        }
    }

    data class LoadData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("type") val type: Int?,
    )

    data class MovieData(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("cats") val cats: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("imdb_id") val imdb_id: String?,
        @JsonProperty("imdb_rating") val imdb_rating: String?,
        @JsonProperty("trailer_url") val trailer_url: String?,
        @JsonProperty("poster_org") val poster_org: String?,
        @JsonProperty("recommend") val recommend: List<Data>,
    )

    data class MovieDataJSON(
        @JsonProperty("data") val data: MovieData,
    )

    data class SeriesSeason(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("season") val season: Int?,
    )

    data class SeriesSeasonJSON(
        @JsonProperty("data") val data: ArrayList<SeriesSeason>,
    )

    data class SeriesEpisode(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("tid") val tid: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("released_timestamp") val released_timestamp: Long?,
        @JsonProperty("imdb_rating") val imdb_rating: String?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("thumbs") val thumbs: String?,
        @JsonProperty("thumbs_bak") val thumbs_bak: String?,
        @JsonProperty("thumbs_min") val thumbs_min: String?,
        @JsonProperty("thumbs_original") val thumbs_original: String?,
        @JsonProperty("thumbs_org") val thumbs_org: String?,
    )

    data class SeriesEpisodeJSON(
        @JsonProperty("data") val data: ArrayList<SeriesEpisode>,
    )

    override suspend fun load(url: String): LoadResponse? {
        val loadData = parseJson<LoadData>(url)
        val query = "{\"id\":\"${loadData.id}\",\"app_id\":\"$appIdSecond\"}"
        val movieData = queryApiParsed<MovieDataJSON>(query)?.data ?: return null
        if (loadData.type == 1) { // Movie
            return newMovieLoadResponse(
                movieData.title ?: "",
                url,
                TvType.Movie,
                LinkData(movieData.id ?: 0, 1, null, null).toJson()
            ) {
                this.recommendations = movieData.recommend.mapNotNull { it.toSearchResponse(this@SuperStreamLegacy) }
                this.posterUrl = movieData.poster_org ?: movieData.poster
                this.year = movieData.year
                this.plot = movieData.description
                this.tags = movieData.cats?.split(",")?.map { it.capitalize() }
                this.addScore(movieData.imdb_rating?.split("/")?.get(0), 10)
                addTrailer(movieData.trailer_url)
                this.addImdbId(movieData.imdb_id)
            }
        } else { // 2 Series
            val data = queryApiParsed<MovieDataJSON>(query)?.data ?: return null
            val seasons = queryApiParsed<SeriesSeasonJSON>(query)?.data ?: return null
            val episodes = seasons.mapNotNull {
                val episodeQuery = "{\"id\":\"${loadData.id}\",\"season\":\"${it.season}\",\"app_id\":\"$appIdSecond\"}"
                queryApiParsed<SeriesEpisodeJSON>(episodeQuery)?.data
            }.flatten()

            return newTvSeriesLoadResponse(
                data.title ?: "",
                url,
                TvType.TvSeries,
                episodes.mapNotNull {
                    newEpisode(LinkData(it.tid ?: it.id ?: return@mapNotNull null, 2, it.season, it.episode).toJson()) {
                        this.name = it.title
                        this.season = it.season
                        this.episode = it.episode
                        this.posterUrl = it.thumbs ?: it.thumbs_bak ?: it.thumbs_min ?: it.thumbs_original ?: it.thumbs_org
                        this.score = Score.from10(it.imdb_rating)
                        this.description = it.synopsis
                        this.addDate(it.released_timestamp?.let { ts -> Date(ts * 1000) })
                    }
                }
            ) {
                this.year = data.year
                this.plot = data.description
                this.posterUrl = data.poster_org ?: data.poster
                this.addScore(data.imdb_rating?.split("/")?.get(0), 10)
                this.tags = data.cats?.split(",")?.map { it.capitalize() }
                this.addImdbId(data.imdb_id)
            }
        }
    }

    data class LinkData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("type") val type: Int,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("episode") val episode: Int?,
    )

    data class LinkSource(
        @JsonProperty("source") val source: String?,
        @JsonProperty("quality") val quality: String?,
        @JsonProperty("is_m3u8") val is_m3u8: Int?,
    )

    data class LinkDataJSON(@JsonProperty("data") val data: ArrayList<LinkSource>)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)
        val query = if (linkData.type == 1) {
            "{\"id\":\"${linkData.id}\",\"app_id\":\"$appIdSecond\"}"
        } else {
            "{\"id\":\"${linkData.id}\",\"season\":\"${linkData.season}\",\"episode\":\"${linkData.episode}\",\"app_id\":\"$appIdSecond\"}"
        }
        val sources = queryApiParsed<LinkDataJSON>(query, true)?.data ?: return false
        sources.forEach {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    it.source ?: return@forEach,
                    if (it.is_m3u8 == 1) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.quality = getQualityFromName(it.quality)
                    if (it.is_m3u8 == 1) this.referer = "https://referrer"
                }
            )
        }
        return true
    }
}
