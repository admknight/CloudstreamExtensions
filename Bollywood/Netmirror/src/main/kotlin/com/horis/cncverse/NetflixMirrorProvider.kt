package com.horis.cncverse

import android.content.Context
import com.horis.cncverse.entities.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class NetflixMirrorProvider : MainAPI() {
    companion object {
        var context: Context? = null
    }

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override var lang = "hi"

    override var mainUrl = "https://net52.cc"

    override var name = "Netflix"

    override val hasMainPage = true
    private var cookie_value = ""
    private val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language" to "en-IN,en-US;q=0.9,en;q=0.8",
        "Cache-Control" to "max-age=0",
        "Connection" to "keep-alive",
        "sec-ch-ua" to "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Android WebView\";v=\"144\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Android\"",
        "sec-ch-ua-platform-version" to "\"13.0.0\"",
        "sec-fetch-dest" to "document",
        "sec-fetch-mode" to "navigate",
        "sec-fetch-site" to "none",
        "sec-fetch-user" to "?1",
        "upgrade-insecure-requests" to "1",
        "user-agent" to "Mozilla/5.0 (Linux; Android 13; SM-A528B Build/TP1A.220624.014; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/114.0.5735.196 Mobile Safari/537.36",
        "x-requested-with" to "com.admknight.netflixmirror"
    )

    override val mainPage = mainPageOf(
        "mobile/home.php?t=${APIHolder.unixTime}" to "Home",
        "mobile/movies.php?t=${APIHolder.unixTime}" to "Movies",
        "mobile/series.php?t=${APIHolder.unixTime}" to "Series",
        "mobile/anime.php?t=${APIHolder.unixTime}" to "Anime",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get("$mainUrl/${request.data}", headers = headers)
        if (cookie_value == "") cookie_value = response.cookies["t_hash_t"] ?: ""
        val data = response.parsed<SearchData>()
        val home = data.searchResult.map {
            newMovieSearchResponse(it.t, "$mainUrl/post/${it.id}", TvType.Movie) {
                this.posterUrl = "https://imgcdn.kim/poster/v/${it.id}.jpg"
            }
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get(
            "$mainUrl/mobile/search.php?s=$query&t=${APIHolder.unixTime}",
            headers = headers
        )
        val data = response.parsed<SearchData>()
        return data.searchResult.map {
            newMovieSearchResponse(it.t, "$mainUrl/post/${it.id}", TvType.Movie) {
                this.posterUrl = "https://imgcdn.kim/poster/v/${it.id}.jpg"
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val cookies = mapOf(
            "t_hash_t" to cookie_value,
            "hd" to "on",
            "ott" to "nf"
        )
        val response = app.get(
            "$mainUrl/mobile/post.php?id=$id&t=${APIHolder.unixTime}",
            cookies = cookies,
            headers = mapOf("Referer" to "$mainUrl/home")
        )
        val data = response.parsed<PostData>()

        val episodes = arrayListOf<Episode>()

        val title = data.title
        val castList = data.cast?.split(",")?.map { it.trim() } ?: emptyList()
        val cast = castList.map {
            ActorData(Actor(it))
        }
        val genre = data.genre?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }

        val score = Score.from10(data.match?.replace("IMDb ", ""))
        val runTime = convertRuntimeToMinutes(data.runtime.toString())


        if (data.episodes?.firstOrNull() == null) {
            episodes.add(newEpisode(LoadData(title, id).toJson()) {
                this.name = data.title
            })
        } else {
            data.episodes.filterNotNull().forEach {
                episodes.add(newEpisode(LoadData(title, it.id).toJson()) {
                    this.name = it.t
                    this.episode = it.ep?.replace("E", "")?.toIntOrNull()
                    this.season = it.s?.replace("S", "")?.toIntOrNull()
                    this.posterUrl = "https://imgcdn.kim/epimg/150/${it.id}.jpg"
                })
            }

            if (data.nextPageShow == 1) {
                episodes.addAll(getEpisodes(title, id, data.nextPageSeason ?: "", 2))
            }

            data.season?.dropLast(1)?.forEach {
                episodes.addAll(getEpisodes(title, id, it.id ?: "", 1))
            }
        }

        val type = if (data.episodes?.firstOrNull() == null) TvType.Movie else TvType.TvSeries

        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl = "https://imgcdn.kim/poster/v/$id.jpg"
            this.backgroundPosterUrl = "https://imgcdn.kim/poster/h/$id.jpg"
            this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
            this.plot = data.desc
            this.year = data.year?.toIntOrNull()
            this.tags = genre
            this.actors = cast
            this.score = score
            this.duration = runTime
            this.contentRating = data.ua
        }
    }

    private suspend fun getEpisodes(
        title: String, eid: String, sid: String, page: Int
    ): List<Episode> {
        val episodes = arrayListOf<Episode>()
        val cookies = mapOf(
            "t_hash_t" to cookie_value,
            "hd" to "on",
            "ott" to "nf"
        )
        var pg = page
        while (true) {
            val res = app.get(
                "$mainUrl/mobile/episodes.php?s=$sid&series=$eid&t=${APIHolder.unixTime}&page=$pg",
                cookies = cookies,
                headers = mapOf("Referer" to "$mainUrl/home")
            )
            val data = res.parsed<EpisodesData>()
            data.episodes?.filterNotNull()?.forEach {
                episodes.add(newEpisode(LoadData(title, it.id).toJson()) {
                    this.name = it.t
                    this.episode = it.ep?.replace("E", "")?.toIntOrNull()
                    this.season = it.s?.replace("S", "")?.toIntOrNull()
                    this.posterUrl = "https://imgcdn.kim/epimg/150/${it.id}.jpg"
                })
            }
            if (data.nextPageShow == 1) {
                pg++
            } else {
                break
            }
        }
        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val cookies = mapOf(
            "t_hash_t" to cookie_value,
            "hd" to "on",
            "ott" to "nf"
        )
        val response = app.get(
            "$mainUrl/mobile/playlist.php?id=${loadData.id}&t=${APIHolder.unixTime}",
            cookies = cookies,
            headers = mapOf("Referer" to "$mainUrl/home")
        )
        val playlist = response.parsed<PlayList>()
        playlist.forEach { item ->
            item.sources.forEach { source ->
                callback.invoke(
                    newExtractorLink(
                        source.label ?: item.title,
                        item.title,
                        source.file.let { if (it.startsWith("//")) "https:$it" else it },
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = getQualityFromName(item.title)
                    }
                )
            }
        }
        return true
    }

    data class LoadData(
        val title: String,
        val id: String?
    )

    private fun convertRuntimeToMinutes(runtime: String): Int? {
        val regex = Regex("(\\d+)h\\s*(\\d+)m|(\\d+)h|(\\d+)m")
        val matchResult = regex.find(runtime) ?: return null

        var minutes = 0
        matchResult.groups[1]?.let { minutes += it.value.toInt() * 60 }
        matchResult.groups[2]?.let { minutes += it.value.toInt() }
        matchResult.groups[3]?.let { minutes += it.value.toInt() * 60 }
        matchResult.groups[4]?.let { minutes += it.value.toInt() }

        return if (minutes > 0) minutes else null
    }
}
