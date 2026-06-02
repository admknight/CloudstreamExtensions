package com.admknight.superstream

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

open class SuperStream(sharedPref: SharedPreferences? = null) : MainAPI() {
    override var name = "SuperStream"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama,
        TvType.Anime
    )

    private val token = sharedPref?.getString("token", null)

    companion object {
        const val Cinemeta = "https://v3-cinemeta.strem.io"
        const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
        const val REMOTE_PROXY_LIST = "https://raw.githubusercontent.com/Stormunblessed/unblessed-files/main/proxies.json"
        private val apiKey = BuildConfig.TMDB_API
        private val febbox = BuildConfig.NuvFeb

        fun getApiBase(): String {
            return febbox
        }

        suspend fun isOfficialAvailable(): Boolean {
            return try {
                app.get("$OFFICIAL_TMDB_URL/configuration?api_key=$apiKey").code == 200
            } catch (e: Exception) {
                false
            }
        }

        suspend fun fetchProxyList(): List<String> {
            return try {
                app.get(REMOTE_PROXY_LIST).parsed<List<String>>()
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun getType(t: String?): TvType {
            return when {
                t?.contains("OVA") == true || t?.contains("Special") == true -> TvType.OVA
                t?.contains("Movie") == true -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }

        fun getStatus(s: String?): ShowStatus {
            return when (s) {
                "Returning Series" -> ShowStatus.Ongoing
                "Ended" -> ShowStatus.Completed
                "Canceled" -> ShowStatus.Completed
                "Pilot" -> ShowStatus.Ongoing
                "In Production" -> ShowStatus.Ongoing
                "Planned" -> ShowStatus.Ongoing
                else -> ShowStatus.Ongoing
            }
        }
    }

    override val mainPage = mainPageOf(
        "$febbox/console/file_share_list?share_key=A9mKzS0Y&parent_id=0&page=1" to "Latest Movies",
        "$febbox/console/file_share_list?share_key=A9mKzS0Y&parent_id=1418701&page=1" to "Latest TV Series",
        "$febbox/console/file_share_list?share_key=A9mKzS0Y&parent_id=1418702&page=1" to "Latest Anime",
        "$febbox/console/file_share_list?share_key=A9mKzS0Y&parent_id=1418703&page=1" to "Latest Asian Drama"
    )

    private fun getImageUrl(url: String?): String? {
        if (url == null) return null
        return if (url.startsWith("http")) url else "${BuildConfig.TMDBIMAGEBASEURL}$url"
    }

    private fun getOriImageUrl(url: String?): String? {
        if (url == null) return null
        return if (url.startsWith("http")) url else "https://image.tmdb.org/t/p/original$url"
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val htmlResponse = app.get(request.data, headers = mapOf("cookie" to (token ?: ""))).parsedSafe<HTML>()
        val doc = Jsoup.parse(htmlResponse?.html.orEmpty())
        val home = doc.select("tr").amap { it.toSearchResponse() }.filterNotNull()

        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String?): SearchResponse? {
        val name = this.name ?: this.title ?: return null
        val id = this.id ?: return null
        return if (type == "movie") {
            newMovieSearchResponse(name, "$id") {
                this.posterUrl = getImageUrl(this@toSearchResponse.posterPath)
            }
        } else {
            newTvSeriesSearchResponse(name, "$id") {
                this.posterUrl = getImageUrl(this@toSearchResponse.posterPath)
            }
        }
    }

    private suspend fun Element.toSearchResponse(): SearchResponse? {
        val title = this.selectFirst("p.file_name_show")?.text() ?: return null
        val fid = this.selectFirst("td[data-id]")?.attr("data-id") ?: return null
        val posterRes = app.get(
            "$febbox/console/share_file_comment?fid=$fid&share_key=A9mKzS0Y",
            headers = mapOf("cookie" to (token ?: ""))
        ).parsedSafe<FebboxPosterResponse>()
        val poster = posterRes?.file?.thumb_big ?: posterRes?.file?.thumb ?: posterRes?.file?.thumb_small
        
        return if (title.contains("S0") || title.contains("Season")) {
            newTvSeriesSearchResponse(title, "$febbox|folder|$fid") {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, "$febbox|$fid") {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? =
        search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$query&language=en-US&page=1&include_adult=false"
        val data = app.get(url).parsedSafe<Results>()
        return data?.results?.mapNotNull {
            it.toSearchResponse(it.mediaType)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        if (url.contains("share_file_comment?fid")) {
            val jsonString = app.get(url, headers = mapOf("cookie" to (token ?: ""))).text
            val response = try { Gson().fromJson(jsonString, PersonalComments::class.java) } catch(_: Exception) { null }
            val media = response?.file ?: return null

            // folder detection
            if (media.is_dir == 1) {
                val folderRes = app.get(
                    "$febbox/console/index_ajax?parent_id=${media.fid}",
                    headers = mapOf("cookie" to (token ?: ""))
                ).parsedSafe<Map<String, Any>>() ?: return null

                val data = folderRes["data"] as? Map<*, *> ?: return null
                val listHtml = data["list"]?.toString() ?: return null
                val doc = Jsoup.parse("<table>$listHtml</table>")

                val episodes = doc.select("tr").mapIndexedNotNull { index, row ->
                    val epFid = row.selectFirst("td[data-id]")?.attr("data-id") ?: return@mapIndexedNotNull null
                    val name = row.selectFirst("p.file_name_show")?.text() ?: return@mapIndexedNotNull null
                    val thumb = row.selectFirst("img")?.attr("src")
                    newEpisode("$febbox|$epFid") {
                        this.name = name
                        this.episode = index + 1
                        this.posterUrl = thumb
                    }
                }

                return newTvSeriesLoadResponse(
                    media.file_name,
                    "$febbox|folder|${media.fid}",
                    TvType.TvSeries,
                    episodes
                ) {
                    this.posterUrl = media.thumb_big ?: "https://wallpapers.com/images/hd/netflix-background-gs7hjuwvv2g0e9fj.jpg"
                    this.plot = "Added: ${media.add_time} | Updated: ${media.update_time}"
                }
            }

            return newMovieLoadResponse(
                media.file_name,
                "$febbox|${media.fid}",
                TvType.Movie,
                "$febbox|${media.fid}"
            ) {
                this.posterUrl = media.thumb_big
                this.plot = "Added: ${media.add_time} | Updated: ${media.update_time}"
            }
        }

        val type = url.split("|").getOrNull(1)
        val id = url.split("|").getOrNull(2)
        val febbox = url.split("|").getOrNull(0)

        if (type == "folder") {
            val folderRes = app.get(
                "$febbox/console/index_ajax?parent_id=$id",
                headers = mapOf("cookie" to (token ?: ""))
            ).parsedSafe<Map<String, Any>>() ?: return null

            val data = folderRes["data"] as? Map<*, *> ?: return null
            val listHtml = data["list"]?.toString() ?: return null
            val doc = Jsoup.parse("<table>$listHtml</table>")

            val episodes = doc.select("tr").mapIndexedNotNull { index, row ->
                val epFid = row.selectFirst("td[data-id]")?.attr("data-id") ?: return@mapIndexedNotNull null
                val name = row.selectFirst("p.file_name_show")?.text() ?: return@mapIndexedNotNull null
                val thumb = row.selectFirst("img")?.attr("src")
                newEpisode("$febbox|$epFid") {
                    this.name = name
                    this.episode = index + 1
                    this.posterUrl = thumb
                }
            }

            return newTvSeriesLoadResponse(
                "Folder $id",
                url,
                TvType.TvSeries,
                episodes
            )
        }

        val isMovie = !url.contains("tv")
        val tmdbId = if (url.startsWith("http")) {
            url.substringAfterLast("/").substringBefore("-")
        } else {
            url
        }

        val tmdbUrl = if (isMovie) {
            "$OFFICIAL_TMDB_URL/movie/$tmdbId?api_key=$apiKey&append_to_response=external_ids,credits,recommendations"
        } else {
            "$OFFICIAL_TMDB_URL/tv/$tmdbId?api_key=$apiKey&append_to_response=external_ids,credits,recommendations"
        }

        val tmdbRes = app.get(tmdbUrl).parsedSafe<LocalTMDbSource>() ?: return null
        val title = tmdbRes.title ?: tmdbRes.name ?: return null
        val poster = getOriImageUrl(tmdbRes.poster_path)
        val bgPoster = getOriImageUrl(tmdbRes.backdrop_path)
        val year = (tmdbRes.release_date ?: tmdbRes.first_air_date)?.split("-")?.firstOrNull()?.toIntOrNull()
        val rating = (tmdbRes.vote_average?.times(10))?.toInt()
        val genres = tmdbRes.genres?.mapNotNull { it.name }

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, "$tmdbId") {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = tmdbRes.overview
                this.tags = genres
                this.score = rating?.let { Score.from100(it.toDouble()) }
                this.addImdbId(tmdbRes.external_ids?.imdb_id)
            }
        } else {
            val episodes = mutableListOf<Episode>()
            val gson = Gson()
            val cineJsonText = app.get("$Cinemeta/meta/series/$tmdbId.json").text
            val cinejson = try { gson.fromJson(cineJsonText, CinemetaRes::class.java) } catch(_: Exception) { null }
            
            val animevideos = cinejson?.meta?.videos
            if (animevideos != null) {
                animevideos.filter { it.season != 0 }.forEach { video ->
                    episodes.add(
                        newEpisode(LinkData(
                            id = tmdbId.toIntOrNull(),
                            imdbId = tmdbRes.external_ids?.imdb_id,
                            season = video.season,
                            episode = video.number,
                            type = "tv",
                            title = title,
                            year = year,
                            airedYear = video.released.split("-").firstOrNull()?.toIntOrNull(),
                            epsTitle = video.name,
                        ).toJson()) {
                            this.name = video.name
                            this.season = video.season
                            this.episode = video.number
                            this.posterUrl = video.thumbnail
                            this.score = Score.from10(video.rating)
                            this.description = video.description
                            this.addDate(video.released)
                        }
                    )
                }
            } else {
                tmdbRes.seasons?.filter { it.season_number != 0 }?.forEach { season ->
                    val seasonRes = app.get("$OFFICIAL_TMDB_URL/tv/$tmdbId/season/${season.season_number}?api_key=$apiKey").parsedSafe<LocalTMDbSeason>()
                    seasonRes?.episodes?.forEach { episode ->
                        episodes.add(
                            newEpisode(LinkData(
                                id = tmdbId.toIntOrNull(),
                                imdbId = tmdbRes.external_ids?.imdb_id,
                                season = season.season_number,
                                episode = episode.episode_number,
                                type = "tv",
                                title = title,
                                year = year,
                                airedYear = episode.air_date?.split("-")?.firstOrNull()?.toIntOrNull(),
                                epsTitle = episode.name,
                            ).toJson()) {
                                this.name = episode.name
                                this.season = season.season_number
                                this.episode = episode.episode_number
                                this.posterUrl = getImageUrl(episode.still_path)
                                this.score = episode.vote_average?.times(10)?.toInt()?.let { Score.from100(it.toDouble()) }
                                this.description = episode.overview
                                this.addDate(episode.air_date)
                            }
                        )
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = tmdbRes.overview
                this.tags = genres
                this.score = rating?.let { Score.from100(it.toDouble()) }
                this.addImdbId(tmdbRes.external_ids?.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith(febbox)) {
            val fid = data.substringAfterLast("|")
            SuperStreamExtractor.invokeSuperstreamFeb(fid, subtitleCallback, callback)
            return true
        }

        val linkData = try { parseJson<LinkData>(data) } catch(_: Exception) { null }
        if (linkData != null) {
            SuperStreamExtractor.invokeSuperstream(linkData, subtitleCallback, callback)
        }
        return true
    }

    data class LocalTMDbSource(
        val title: String? = null,
        val name: String? = null,
        val poster_path: String? = null,
        val backdrop_path: String? = null,
        val release_date: String? = null,
        val first_air_date: String? = null,
        val vote_average: Double? = null,
        val genres: List<Genre>? = null,
        val overview: String? = null,
        val external_ids: ExternalIds? = null,
        val seasons: List<Season>? = null
    ) {
        data class Genre(val name: String? = null)
        data class ExternalIds(val imdb_id: String? = null)
        data class Season(val season_number: Int)
    }

    data class LocalTMDbSeason(
        val episodes: List<Episode>? = null
    ) {
        data class Episode(
            val episode_number: Int,
            val name: String? = null,
            val still_path: String? = null,
            val vote_average: Double? = null,
            val overview: String? = null,
            val air_date: String? = null
        )
    }

    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val epid: Int? = null,
        val aniId: String? = null,
        val animeId: String? = null,
        val title: String? = null,
        val year: Int? = null,
        val orgTitle: String? = null,
        val isAnime: Boolean = false,
        val airedYear: Int? = null,
        val lastSeason: Int? = null,
        val epsTitle: String? = null,
        val jpTitle: String? = null,
        val date: String? = null,
        val airedDate: String? = null,
        val isAsian: Boolean = false,
        val isBollywood: Boolean = false,
        val isCartoon: Boolean = false,
        val alttitle: String? = null,
        val nametitle: String? = null,
    )

    data class Data(@JsonProperty("id") val id: Int?, @JsonProperty("type") val type: String?, @JsonProperty("aniId") val aniId: String?, @JsonProperty("malId") val malId: Int?)
    data class Results(@JsonProperty("results") val results: ArrayList<Media>?)
    data class Media(@JsonProperty("id") val id: Int?, @JsonProperty("name") val name: String?, @JsonProperty("title") val title: String?, @JsonProperty("original_name") val originalName: String?, @JsonProperty("media_type") val mediaType: String?, @JsonProperty("poster_path") val posterPath: String?, @JsonProperty("vote_average") val voteAverage: Double?)
}
