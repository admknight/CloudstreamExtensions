package com.admknight.torrastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

object TorraStreamExtractor {
    suspend fun invokeTorrentioDebian(url: String, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeMeteorDebian(url: String, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeAIOStreamsDebian(key: String, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeDebianTorbox(url: String, key: String, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeTorrentio(url: String, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeThepiratebay(url: String, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeAnimetosho(anidbEid: Int?, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeTorrentioAnime(url: String, kitsuId: Int, season: Int?, episode: Int?, filtered: Boolean) {}
    suspend fun invokeUindex(url: String, title: String?, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeTorrentsDB(url: String, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeTorrentsDBAnime(url: String, kitsuId: Int, id: Int, episode: Int, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeKnaben(url: String, title: String?, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {}
    suspend fun invokeSubtitleAPI(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {}

    // Anime specific
    suspend fun invokeTorrentioAnimeDebian(url: String, type: String, kitsuId: Int, episode: Int, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeTorboxAnimeDebian(url: String, key: String, type: String, kitsuId: Int, episode: Int, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeMeteorAnimeDebian(url: String, type: String, kitsuId: Int, episode: Int, callback: (ExtractorLink) -> Unit, filtered: Boolean) {}
    suspend fun invokeTorrentioAnimeType(url: String, type: String, kitsuId: Int, episode: Int, callback: (ExtractorLink) -> Unit) {}
}
