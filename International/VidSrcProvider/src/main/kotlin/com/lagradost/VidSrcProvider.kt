package com.lagradost

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class VidSrcProvider : TmdbProvider() {
    override val apiName = "VidSrc"
    override var name = "VidSrc"
    override var mainUrl = "https://v2.vidsrc.me"
    override val useMetaLoadResponse = true
    override val instantLinkLoading = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = parseJson<TmdbLink>(data)
        val id = mappedData.imdbID ?: mappedData.tmdbID.toString()
        
        val isMovie = mappedData.episode == null && mappedData.season == null
        val embedUrl = if (isMovie) {
             "$mainUrl/embed/$id"
        } else {
            val suffix = "$id/${mappedData.season ?: 1}-${mappedData.episode ?: 1}"
             "$mainUrl/embed/$suffix"
        }

        loadExtractor(embedUrl, null, subtitleCallback, callback)

        return true
    }
}
