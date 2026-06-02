package com.admknight.torrastream

import android.content.SharedPreferences
import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.syncproviders.SyncIdName

open class TorraStream(private val sharedPref: SharedPreferences) : TmdbProvider() {
    override var name = "TorraStream"
    override var mainUrl = "https://torrentio.strem.fun"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = true

    companion object {
        private const val Cinemeta = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb"
        const val ThePirateBayApi = "https://thepiratebay-plus.strem.fun"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val AnimetoshoAPI = "https://feed.animetosho.org"
        const val TorrentioAnimeAPI = "https://torrentio.strem.fun/providers=nyaasi,tokyotosho,anidex%7Csort=seeders"
        const val TorboxAPI= "https://stremio.torbox.app"
        val TRACKER_LIST_URL = listOf(
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best.txt",
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best_ip.txt",
        )
        private const val Uindex = "https://uindex.org"
        private const val Knaben = "https://knaben.org"
        private const val TorrentsDB = "https://torrentsdb.com"
        const val Meteorfortheweebs ="https://meteorfortheweebs.midnightignite.me"
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = "1865f43a0549ca50d341dd9ab8b29f49"
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val dataObj = parseJson<LinkData>(data)
        val id = dataObj.imdbId
        val kitsuId = dataObj.kitsuId ?: -1
        val season = dataObj.season
        val episode = dataObj.episode
        val title = dataObj.title
        val year = dataObj.year
        val provider = sharedPref.getString("torrastream_provider", "Torrentio")
        val key = sharedPref.getString("torrastream_key", "")
        val anidbEid = dataObj.anidbEid
        
        val torrentioapiUrl = "https://torrentio.strem.fun"
        val meteorUrl = Meteorfortheweebs
        val filtered = false

        runAllAsync(
            { TorraStreamExtractor.invokeTorrentioDebian(torrentioapiUrl, id, season, episode, callback, filtered) },
            { TorraStreamExtractor.invokeMeteorDebian(meteorUrl, id, season, episode, callback, filtered) },
            { TorraStreamExtractor.invokeAIOStreamsDebian(key ?: "", id, season, episode, callback, filtered) },
            { TorraStreamExtractor.invokeDebianTorbox(TorboxAPI, key ?: "", id, season, episode, callback, filtered) },
            { TorraStreamExtractor.invokeTorrentio(torrentioapiUrl, id, season, episode, callback, filtered) },
            { TorraStreamExtractor.invokeThepiratebay(ThePirateBayApi, id, season, episode, callback) },
            { TorraStreamExtractor.invokeAnimetosho(anidbEid, callback) },
            { TorraStreamExtractor.invokeTorrentioAnime(TorrentioAnimeAPI, kitsuId, season, episode, filtered) },
            { TorraStreamExtractor.invokeUindex(Uindex, title, year, season, episode, callback) },
            { TorraStreamExtractor.invokeTorrentsDB(TorrentsDB, id, season, episode, callback, filtered) },
            { TorraStreamExtractor.invokeTorrentsDBAnime(TorrentsDB, kitsuId, kitsuId, episode ?: 1, callback, filtered) },
            { TorraStreamExtractor.invokeKnaben(Knaben, title, year, season, episode, callback) },
            { TorraStreamExtractor.invokeSubtitleAPI(id, season, episode, subtitleCallback) }
        )

        return true
    }

    data class LinkData(
        val imdbId: String? = null,
        val kitsuId: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val isAnime: Boolean = false,
        val anidbEid: Int? = null
    )
}
