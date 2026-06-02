package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import org.jsoup.nodes.Element

class MultiMoviesProvider : MainAPI() {
    override var mainUrl: String = runBlocking {
        MultiMoviesProviderPlugin.getDomains()?.MultiMovies ?: "https://multimovies.autos"
    }
    override var name = "MultiMovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AnimeMovie
    )

    override val mainPage = mainPageOf(
        "trending/" to "Trending",
        "genre/bollywood-movies/" to "Bollywood Movies",
        "genre/hollywood/" to "Hollywood Movies",
        "genre/south-indian/" to "South Indian Movies",
        "genre/punjabi/" to "Punjabi Movies",
        "genre/amazon-prime/" to "Amazon Prime",
        "genre/disney-hotstar/" to "Disney Hotstar",
        "genre/jio-ott/" to "Jio OTT",
        "genre/netflix/" to "Netfilx",
        "genre/sony-liv/" to "Sony Live",
        "genre/k-drama/" to "KDrama",
        "genre/zee-5/" to "Zee5",
        "genre/anime-hindi/" to "Anime Series",
        "genre/anime-movies/" to "Anime Movies",
        "genre/cartoon-network/" to "Cartoon Network",
        "genre/disney-channel/" to "Disney Channel",
        "genre/hungama/" to "Hungama",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "$mainUrl/${request.data}" else "$mainUrl/${request.data}page/$page/"
        val document = app.get(url).document
        val items = if (request.data.contains("/movies")) {
            document.select("#archive-content > article").mapNotNull { it.toSearchResult() }
        } else {
            document.select("div.items > article").mapNotNull { it.toSearchResult() }
        }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data > h3 > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.data > h3 > a")?.attr("href") ?: "")
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.getImageAttr())
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text())
        val isMovie = href.contains("/movies/") || href.contains("/movie/")
        
        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.result-item").mapNotNull {
            val a = it.selectFirst("article > div.details > div.title > a") ?: return@mapNotNull null
            val title = a.text().trim()
            val href = fixUrl(a.attr("href"))
            val posterUrl = fixUrlNull(it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src"))
            val quality = getQualityFromString(it.select("div.poster > div.mepo > span").text())
            val type = it.select("article > div.image > div.thumbnail > a > span").text()
            
            if (type.contains("Movie")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }

    data class TrailerUrl(@JsonProperty("embed_url") var embedUrl: String?, @JsonProperty("type") var type: String?)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val titleElement = doc.selectFirst("div.sheader > div.data > h1") ?: return null
        val hTitle = titleElement.text().trim()
        val title = Regex("(^.*\\)\\d*)").find(hTitle)?.groupValues?.get(1) ?: hTitle
        
        val poster = fixUrlNull(doc.select("div.poster img").attr("src"))
        val bgPoster = fixUrlNull(doc.select("div.g-item a").attr("href"))
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year = doc.selectFirst("span.date")?.text()?.substringAfter(",")?.trim()?.toIntOrNull()
        val description = doc.selectFirst("#info div.wp-content p")?.text()?.trim()
        val isSeries = url.contains("tvshows")
        
        var trailerUrl: String? = null
        if (!isSeries) {
            runCatching {
                val postId = doc.select("#player-option-trailer").attr("data-post")
                val body = FormBody.Builder().addEncoded("action", "doo_player_ajax").addEncoded("post", postId).addEncoded("nume", "trailer").addEncoded("type", "movie").build()
                val res = app.post("$mainUrl/wp-admin/admin-ajax.php", requestBody = body, referer = url).parsed<TrailerUrl>()
                trailerUrl = res.embedUrl?.let { Regex("\"http.*\"").find(it)?.value?.trim('"') }
            }
        } else {
            trailerUrl = fixUrlNull(doc.select("iframe.rptss").attr("src"))
        }

        val scoreValue = Score.from10(doc.select("span.dt_rating_vgs").text())
        val duration = doc.selectFirst("span.runtime")?.text()?.removeSuffix(" Min.")?.trim()?.toIntOrNull()
        
        val actors = doc.select("div.person").map {
            ActorData(Actor(it.select("div.data > div.name > a").text(), it.select("div.img > a > img").attr("src")), roleString = it.select("div.data > div.caracter").text())
        }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull { it.toSearchResult() }

        if (isSeries) {
            val episodes = ArrayList<Episode>()
            doc.select("#seasons ul.episodios").forEachIndexed { sIdx, ul ->
                ul.select("li").forEachIndexed { eIdx, li ->
                    val a = li.selectFirst("div.episodiotitle > a")
                    if (a != null) {
                        episodes.add(newEpisode(a.attr("href")) {
                            this.name = a.text()
                            this.season = sIdx + 1
                            this.episode = eIdx + 1
                            this.posterUrl = li.selectFirst("div.imagen > img")?.getImageAttr()
                        })
                    }
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgPoster ?: poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = scoreValue
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailerUrl)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgPoster ?: poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = scoreValue
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailerUrl)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val req = app.get(data).document
        req.select("ul#playeroptionsul li").amap { li ->
            val id = li.attr("data-post")
            val nume = li.attr("data-nume")
            val type = li.attr("data-type")
            
            if (!nume.contains("trailer")) {
                val res = app.post(
                    url = "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type),
                    referer = mainUrl,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>()
                
                val source = res.embed_url
                val link = Regex("""SRC="(https?:[^"]+)""" , RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)?.replace("\t", "")?.trim() 
                    ?: source.substringAfter("\"").substringBefore("\"").trim()
                
                if (!link.contains("youtube")) {
                    if (link.contains("deaddrive.xyz")) {
                        app.get(link).document.select("ul.list-server-items > li").forEach {
                            loadExtractor(it.attr("data-video"), mainUrl, subtitleCallback, callback)
                        }
                    } else {
                        loadExtractor(link, mainUrl, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }

    data class ResponseHash(@JsonProperty("embed_url") val embed_url: String)

    private fun Element.getImageAttr(): String? {
        return this.attr("data-src").takeIf { it.startsWith("http") } ?: this.attr("src").takeIf { it.startsWith("http") }
    }
}
