package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import java.net.URI

class NginxProvider : MainAPI() {
    override var name = "Nginx"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)

    companion object {
        var loginCredentials: String? = null
        var overrideUrl: String? = null
        var pathToLibrary: String? = null
        const val ERROR_STRING = "No nginx url specified"
    }

    private fun getAuthHeader(): Map<String, String> {
        val url = overrideUrl ?: throw ErrorLoadingException(ERROR_STRING)
        mainUrl = url
        val creds = loginCredentials
        return if (creds != null && creds.trim() != ":") {
            mapOf("Authorization" to "Basic ${base64Encode(creds.toByteArray())}")
        } else mapOf()
    }

    override suspend fun load(url: String): LoadResponse? {
        val authHeader = getAuthHeader()
        val isValid = url.contains(".nfo")
        val isSerie = url.contains("tvshow.nfo")
        val metadataDocument = app.get(url, headers = authHeader).document

        val title = metadataDocument.selectFirst("title")?.text()?.replace("/", "") ?: url.substringBeforeLast("/").substringAfterLast("/")
        val description = metadataDocument.selectFirst("plot")?.text()
        val mediaRootUrl = url.substringBeforeLast("/") + "/"
        val mediaRootDocument = app.get(mediaRootUrl, headers = authHeader).document

        if (!isValid) {
            if (".mp4" in url || ".mkv" in url) {
                return newMovieLoadResponse(title, url, TvType.Movie, url)
            }
            return null
        }

        if (!isSerie) {
            val poster = metadataDocument.selectFirst("thumb[aspect=poster]")?.text()
            val fanart = metadataDocument.selectFirst("fanart > thumb")?.text()
            val trailer = metadataDocument.selectFirst("trailer")?.text()?.replace("plugin://plugin.video.youtube/play/?video_id=", "https://www.youtube.com/watch?v=")
            val year = metadataDocument.selectFirst("year")?.text()?.toIntOrNull()
            val scoreValue = Score.from10(metadataDocument.selectFirst("value")?.text())
            val tags = metadataDocument.select("genre").map { it.text() }
            val actors = metadataDocument.select("actor").mapNotNull {
                val aName = it.selectFirst("name")?.text() ?: return@mapNotNull null
                val aThumb = it.selectFirst("thumb")?.text() ?: ""
                Actor(aName, aThumb)
            }

            val mkv = mediaRootDocument.selectFirst("a[href$=.mkv]")?.attr("href")
            val mp4 = mediaRootDocument.selectFirst("a[href$=.mp4]")?.attr("href")
            val dataUrl = mkv ?: mp4

            if (dataUrl != null) {
                return newMovieLoadResponse(title, mediaRootUrl, TvType.Movie, mediaRootUrl + dataUrl) {
                    this.year = year
                    this.plot = description
                    this.score = scoreValue
                    this.tags = tags
                    this.backgroundPosterUrl = fanart
                    addPoster(poster, authHeader)
                    addActors(actors)
                    addTrailer(trailer)
                }
            }
        } else {
            val seasons = mediaRootDocument.select("a[href*=Season%20]")
            val seasonList = seasons.mapNotNull {
                val sNum = it.attr("href").replace("Season%20", "").replace("/", "").toIntOrNull()
                if (sNum != null) Pair(sNum, mediaRootUrl + it.attr("href")) else null
            }

            if (seasonList.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodes = ArrayList<Episode>()
            seasonList.forEach { (sNum, sUrl) ->
                val sDoc = app.get(sUrl, headers = authHeader).document
                sDoc.select("a[href$=.nfo]").forEach { ep ->
                    val nfoDoc = app.get(sUrl + ep.attr("href"), headers = authHeader).document
                    val epNum = nfoDoc.selectFirst("episode")?.text()?.toIntOrNull()
                    val epTitle = nfoDoc.selectFirst("title")?.text()
                    val epPlot = nfoDoc.selectFirst("plot")?.text()
                    val epDate = nfoDoc.selectFirst("aired")?.text()
                    
                    val mediaLink = sDoc.selectFirst("a[href^=${ep.attr("href").removeSuffix(".nfo")}]:not([href$=.nfo]):not([href$=.jpg])")?.attr("href")
                    if (mediaLink != null) {
                        episodes.add(newEpisode(sUrl + mediaLink) {
                            this.name = epTitle
                            this.season = sNum
                            this.episode = epNum
                            this.description = epPlot
                            addDate(epDate)
                        })
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.plot = description
                this.tags = metadataDocument.select("genre").map { it.text() }
                addPoster(mediaRootUrl + "poster.jpg", authHeader)
            }
        }
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val authHeader = getAuthHeader()
        runCatching {
            val root = data.substringBeforeLast("/") + "/"
            val doc = app.get(root, headers = authHeader).document
            doc.select("a[href$=.srt]").forEach {
                val lang = SubtitleHelper.fromTwoLettersToLanguage(it.attr("href").removeSuffix(".srt").substringAfterLast(".")) ?: "English"
                subtitleCallback.invoke(SubtitleFile(lang, root + it.attr("href")))
            }
        }

        callback.invoke(newExtractorLink(name, name, data, INFER_TYPE) {
            this.quality = Qualities.Unknown.value
        })
        return true
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val authHeader = getAuthHeader()
        if (mainUrl.isEmpty() || mainUrl == "NONE") throw ErrorLoadingException("No nginx url specified")

        val document = app.get(mainUrl, headers = authHeader).document
        val cats = document.select("a").filter { it.text() != "../" && it.text() != "/" }
        
        val sections = cats.mapNotNull { cat ->
            val catUrl = mainUrl + cat.attr("href").removePrefix("/")
            val catDoc = app.get(catUrl, headers = authHeader).document
            val links = catDoc.select("a").filter { it.text() != "../" && it.text() != "/" }
            
            val results = links.mapNotNull { item ->
                val itemUrl = catUrl + item.attr("href").removePrefix("/")
                val isFolder = item.attr("href").endsWith("/")
                
                if (isFolder) {
                    val itemDoc = app.get(itemUrl, headers = authHeader).document
                    val nfo = itemDoc.selectFirst("a[href$=.nfo]")?.attr("href")
                    if (nfo != null) {
                        val nfoUrl = itemUrl + nfo
                        val nfoDoc = app.get(nfoUrl, headers = authHeader).document
                        val isSerie = nfo == "tvshow.nfo"
                        val title = nfoDoc.selectFirst("title")?.text() ?: item.text()
                        
                        if (isSerie) {
                            newTvSeriesSearchResponse(title, nfoUrl, TvType.TvSeries) {
                                addPoster(itemUrl + "poster.jpg", authHeader)
                            }
                        } else {
                            newMovieSearchResponse(title, nfoUrl, TvType.Movie) {
                                addPoster(nfoDoc.selectFirst("thumb[aspect=poster]")?.text(), authHeader)
                            }
                        }
                    } else {
                        newMovieSearchResponse(item.text(), itemUrl, TvType.Movie) {}
                    }
                } else {
                    newMovieSearchResponse(item.text(), itemUrl, TvType.Movie) {}
                }
            }
            if (results.isNotEmpty()) HomePageList(cat.text().replace("/", ""), results) else null
        }

        return newHomePageResponse(sections)
    }
}
