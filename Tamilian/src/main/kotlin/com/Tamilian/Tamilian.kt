package com.Tamilian

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink


class Tamilian : TmdbProvider() {
    override var name = "Tamilian"
    override val hasMainPage = true
    override var lang = "ta"
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
    )

    companion object
    {
        const val HOST="https://embedojo.net"
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<TmdbLink>(data).toLinkData()
        val script = app.get("$HOST/tamil/tmdb/${mediaData.tmdbId}")
            .document.selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data()?.let { getAndUnpack(it) }

        val token = script?.substringAfter("FirePlayer(\"")?.substringBefore("\",")
        val m3u8 = app.post("$HOST/player/index.php?data=$token&do=getVideo", headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
            .parsedSafe<VideoData>()
        val headers= mapOf("Origin" to "https://embedojo.net")
        m3u8?.let {
            safeApiCall {
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        url = it.videoSource,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P1080.value
                        this.headers = headers
                    }
                )
            }
        }
        return true
    }


    private fun TmdbLink.toLinkData(): LinkData {
        return LinkData(
            imdbId = imdbID,
            tmdbId = tmdbID,
            title = movieName,
            season = season,
            episode = episode
        )
    }


    data class LinkData(
        @param:JsonProperty("simklId") val simklId: Int? = null,
        @param:JsonProperty("traktId") val traktId: Int? = null,
        @param:JsonProperty("imdbId") val imdbId: String? = null,
        @param:JsonProperty("tmdbId") val tmdbId: Int? = null,
        @param:JsonProperty("tvdbId") val tvdbId: Int? = null,
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("season") val season: Int? = null,
        @param:JsonProperty("episode") val episode: Int? = null,
        @param:JsonProperty("aniId") val aniId: String? = null,
        @param:JsonProperty("malId") val malId: String? = null,
        @param:JsonProperty("title") val title: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("orgTitle") val orgTitle: String? = null,
        @param:JsonProperty("isAnime") val isAnime: Boolean = false,
        @param:JsonProperty("airedYear") val airedYear: Int? = null,
        @param:JsonProperty("lastSeason") val lastSeason: Int? = null,
        @param:JsonProperty("epsTitle") val epsTitle: String? = null,
        @param:JsonProperty("jpTitle") val jpTitle: String? = null,
        @param:JsonProperty("date") val date: String? = null,
        @param:JsonProperty("airedDate") val airedDate: String? = null,
        @param:JsonProperty("isAsian") val isAsian: Boolean = false,
        @param:JsonProperty("isBollywood") val isBollywood: Boolean = false,
        @param:JsonProperty("isCartoon") val isCartoon: Boolean = false,
    )


    data class VideoData(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<Any?>,
        val attachmentLinks: List<Any?>,
        val ck: String,
    )

}