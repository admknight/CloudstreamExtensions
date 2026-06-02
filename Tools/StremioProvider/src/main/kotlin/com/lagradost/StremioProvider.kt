package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.json.JSONObject
import java.net.URLEncoder

private const val TRACKER_LIST_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"

class StremioProvider : MainAPI() {
    override var mainUrl = "https://stremio.github.io/stremio-static-addon-example"
    override var name = "Stremio example"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse? {
        val res = tryParseJson<Manifest>(app.get("${mainUrl}/manifest.json").text) ?: return null
        val lists = mutableListOf<HomePageList>()
        res.catalogs.forEach { catalog ->
            catalog.toHomePageList(this)?.let { lists.add(it) }
        }
        return newHomePageResponse(lists, false)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val res = tryParseJson<Manifest>(app.get("${mainUrl}/manifest.json").text) ?: return null
        val list = mutableListOf<SearchResponse>()
        res.catalogs.forEach { catalog ->
            list.addAll(catalog.search(query, this))
        }
        return list
    }

    override suspend fun load(url: String): LoadResponse? {
        val res = tryParseJson<CatalogEntry>(url) ?: throw RuntimeException(url)
        return res.toLoadResponse(this)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = tryParseJson<StreamsResponse>(app.get(data).text) ?: return false
        res.streams.forEach { it.runCallback(subtitleCallback, callback) }
        return true
    }

    private data class Manifest(val catalogs: List<Catalog>)
    private data class Catalog(
        var name: String?,
        val id: String,
        val type: String?,
        val types: MutableList<String> = mutableListOf()
    ) {
        init { if (type != null) types.add(type) }

        suspend fun search(query: String, provider: StremioProvider): List<SearchResponse> {
            val entries = mutableListOf<SearchResponse>()
            types.forEach { t ->
                val res = tryParseJson<CatalogResponse>(app.get("${provider.mainUrl}/catalog/${t.encodeUri()}/${id.encodeUri()}/search=${query.encodeUri()}.json").text) ?: return@forEach
                res.metas.forEach { entries.add(it.toSearchResponse(provider)) }
            }
            return entries
        }

        suspend fun toHomePageList(provider: StremioProvider): HomePageList? {
            val entries = mutableListOf<SearchResponse>()
            types.forEach { t ->
                val res = tryParseJson<CatalogResponse>(app.get("${provider.mainUrl}/catalog/${t.encodeUri()}/${id.encodeUri()}.json").text) ?: return@forEach
                res.metas.forEach { entries.add(it.toSearchResponse(provider)) }
            }
            return if (entries.isNotEmpty()) HomePageList(name ?: id, entries) else null
        }
    }

    private data class CatalogResponse(val metas: List<CatalogEntry>)
    private data class CatalogEntry(
        val name: String,
        val id: String,
        val poster: String?,
        val description: String?,
        val type: String?,
        val videos: List<Video>?
    ) {
        fun toSearchResponse(provider: StremioProvider): SearchResponse {
            return provider.newMovieSearchResponse(name, this.toJson(), TvType.Others) {
                posterUrl = poster
            }
        }
        suspend fun toLoadResponse(provider: StremioProvider): LoadResponse {
            return if (videos.isNullOrEmpty()) {
                provider.newMovieLoadResponse(name, "${provider.mainUrl}/meta/${type?.encodeUri()}/${id.encodeUri()}.json", TvType.Others, "${provider.mainUrl}/stream/${type?.encodeUri()}/${id.encodeUri()}.json") {
                    posterUrl = poster
                    plot = description
                }
            } else {
                provider.newTvSeriesLoadResponse(name, "${provider.mainUrl}/meta/${type?.encodeUri()}/${id.encodeUri()}.json", TvType.Others, videos.map { it.toEpisode(provider, type) }) {
                    posterUrl = poster
                    plot = description
                }
            }
        }
    }

    private data class Video(val id: String, val title: String?, val thumbnail: String?, val overview: String?) {
        fun toEpisode(provider: StremioProvider, type: String?): Episode {
            return provider.newEpisode("${provider.mainUrl}/stream/${type?.encodeUri()}/${id.encodeUri()}.json") {
                this.name = title
                this.posterUrl = thumbnail
                this.description = overview
            }
        }
    }

    private data class StreamsResponse(val streams: List<Stream>)
    private data class Stream(
        val name: String?,
        val title: String?,
        val url: String?,
        val ytId: String?,
        val externalUrl: String?,
        val behaviorHints: JSONObject?,
        val infoHash: String?,
        val sources: List<String> = emptyList()
    ) {
        suspend fun runCallback(subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
            if (url != null) {
                var ref: String? = null
                runCatching {
                    val headers = ((behaviorHints?.get("proxyHeaders") as? JSONObject)?.get("request") as? JSONObject)
                    ref = headers?.get("referer") as? String ?: headers?.get("origin") as? String
                }
                
                callback.invoke(newExtractorLink(name ?: "", title ?: name ?: "", url, INFER_TYPE) {
                    this.referer = ref ?: ""
                    this.quality = Qualities.Unknown.value
                })
            }
            if (ytId != null) {
                loadExtractor("https://www.youtube.com/watch?v=$ytId", subtitleCallback, callback)
            }
            if (externalUrl != null) {
                loadExtractor(externalUrl, subtitleCallback, callback)
            }
            if (infoHash != null) {
                val resp = app.get(TRACKER_LIST_URL).text
                val trackers = resp.split("\n").filterIndexed { i, _ -> i % 2 == 0 }.filter { it.isNotBlank() }.joinToString("") { "&tr=$it" }
                val sTrackers = sources.filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") }.filter { it.isNotBlank() }.joinToString("") { "&tr=$it" }
                val magnet = "magnet:?xt=urn:btih:${infoHash}${sTrackers}${trackers}"
                
                callback.invoke(newExtractorLink(name ?: "", title ?: name ?: "", magnet, INFER_TYPE) {
                    this.quality = Qualities.Unknown.value
                })
            }
        }
    }

    companion object {
        fun String.encodeUri() = URLEncoder.encode(this, "utf8")
    }
}
