package com.horis.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.horis.cncverse.entities.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class PrimeVideoMirrorProvider : MainAPI() {
    override var name = "Prime Video"
    override var mainUrl = "https://netmirror.org"
    private var cookie_value = ""

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val cookies = mapOf(
            "t_hash_t" to cookie_value,
            "hd" to "on",
            "ott" to "pv"
        )
        val response = app.get(
            "$mainUrl/mobile/hs/post.php?id=$id&t=${APIHolder.unixTime}",
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
                    this.posterUrl = "https://imgcdn.kim/hsepimg/150/${it.id}.jpg"
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
            this.posterUrl = "https://imgcdn.kim/hs/v/$id.jpg"
            this.backgroundPosterUrl = "https://imgcdn.kim/hs/h/$id.jpg"
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
            "ott" to "pv"
        )
        var pg = page
        while (true) {
            val res = app.get(
                "$mainUrl/mobile/hs/episodes.php?s=$sid&series=$eid&t=${APIHolder.unixTime}&page=$pg",
                cookies = cookies,
                headers = mapOf("Referer" to "$mainUrl/home")
            )
            val data = res.parsed<EpisodesData>()
            data.episodes?.filterNotNull()?.forEach {
                episodes.add(newEpisode(LoadData(title, it.id).toJson()) {
                    this.name = it.t
                    this.episode = it.ep?.replace("E", "")?.toIntOrNull()
                    this.season = it.s?.replace("S", "")?.toIntOrNull()
                    this.posterUrl = "https://imgcdn.kim/hsepimg/150/${it.id}.jpg"
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
            "ott" to "pv"
        )
        val response = app.get(
            "$mainUrl/mobile/hs/playlist.php?id=${loadData.id}&t=${APIHolder.unixTime}",
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
