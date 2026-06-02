package com.admknight.bflix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.*

open class BflixProvider : MainAPI() {
    override var mainUrl = "https://bflix.ru"
    override var name = "Bflix"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    companion object {
        private val mediaType = "application/json; charset=utf-8".toMediaType()
        private const val bfliKey = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private const val mainKey = "DZmuZuXqa9O0z3b7"

        fun encodeVrf(text: String, key: String): String {
            return encode(encrypt(cipher(key, encode(text)), bfliKey))
        }

        fun decodeVrf(text: String, key: String): String {
            return decode(cipher(key, decrypt(text, bfliKey)))
        }

        fun encrypt(input: String, key: String): String {
            if (input.any { it.code > 255 }) throw Exception("illegal characters!")
            var output = ""
            for (i in input.indices step 3) {
                val a = intArrayOf(-1, -1, -1, -1)
                a[0] = input[i].code shr 2
                a[1] = (3 and input[i].code) shl 4
                if (input.length > i + 1) {
                    a[1] = a[1] or (input[i + 1].code shr 4)
                    a[2] = (15 and input[i + 1].code) shl 2
                }
                if (input.length > i + 2) {
                    a[2] = a[2] or (input[i + 2].code shr 6)
                    a[3] = 63 and input[i + 2].code
                }
                for (n in a) {
                    if (n == -1) output += "="
                    else {
                        if (n in 0..63) output += key[n]
                    }
                }
            }
            return output
        }

        fun cipher(key: String, text: String): String {
            val arr = IntArray(256) { it }
            var u = 0
            var r: Int
            arr.indices.forEach {
                u = (u + arr[it] + key[it % key.length].code) % 256
                r = arr[it]
                arr[it] = arr[u]
                arr[u] = r
            }
            u = 0
            var c = 0
            return text.indices.map { j ->
                c = (c + 1) % 256
                u = (u + arr[c]) % 256
                r = arr[c]
                arr[c] = arr[u]
                arr[u] = r
                (text[j].code xor arr[(arr[c] + arr[u]) % 256]).toChar()
            }.joinToString("")
        }

        private fun decrypt(input: String, key: String): String {
            val t = if (input.replace("""[\t\n\f\r]""".toRegex(), "").length % 4 == 0) {
                input.replace("""==?$""".toRegex(), "")
            } else input
            if (t.length % 4 == 1 || t.contains("""[^+/0-9A-Za-z]""".toRegex())) throw Exception("bad input")
            var i: Int
            var r = ""
            var e = 0
            var u = 0
            for (o in t.indices) {
                e = e shl 6
                i = key.indexOf(t[o])
                e = e or i
                u += 6
                if (24 == u) {
                    r += ((16711680 and e) shr 16).toChar()
                    r += ((65280 and e) shr 8).toChar()
                    r += (255 and e).toChar()
                    e = 0
                    u = 0
                }
            }
            return if (12 == u) {
                e = e shr 4
                r + e.toChar()
            } else {
                if (18 == u) {
                    e = e shr 2
                    r += ((65280 and e) shr 8).toChar()
                    r += (255 and e).toChar()
                }
                r
            }
        }

        fun encode(input: String): String = java.net.URLEncoder.encode(input, "utf-8").replace("+", "%20")
        private fun decode(input: String): String = java.net.URLDecoder.decode(input, "utf-8")
    }


    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get("$mainUrl/home").document
        val testa = listOf(
            Pair("Movies", "div.tab-content[data-name=movies] div.film div.film-inner"),
            Pair("Shows", "div.tab-content[data-name=shows] div.film div.film-inner"),
            Pair("Trending", "div.tab-content[data-name=trending] div.film div.film-inner"),
            Pair("Latest Movies", "div.zone:contains(Latest Movies) div.film div.film-inner"),
            Pair("Latest TV Shows", "div.zone:contains(Latest TV Shows) div.film div.film-inner"),
        )

        testa.forEach {(name, element) ->
            val results = soup.select(element).map {
                val title = it.selectFirst("a.film-name")?.text() ?: ""
                val link = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
                val poster = it.selectFirst("a.film-poster img")?.attr("data-src")
                val isMovie = link.contains("/movie/")
                
                newMovieSearchResponse(title, link, if (isMovie) TvType.Movie else TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
            items.add(HomePageList(name, results))
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/filter?keyword=$query"
        val document = app.get(url).document
        return document.select("div.film div.film-inner").map {
            val title = it.selectFirst("a.film-name")?.text() ?: ""
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            val image = it.selectFirst("a.film-poster img")?.attr("data-src")
            val isMovie = href.contains("/movie/")

            newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.TvSeries) {
                this.posterUrl = image
            }
        }
    }

    data class Response(
        @JsonProperty("result") val result: String
    )

    data class VrfResponse (
        @JsonProperty("url" ) val url : String
    )

    private suspend fun vrfHelper(action:String, query: String): String {
        val parse = app.get("https://9anime.eltik.net/$action?query=$query&apikey=lagrapps").parsed<VrfResponse>()
        return parse.url
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val movieid = soup.selectFirst("div.film-rating")?.attr("data-id") ?: return null
        val movieidencoded = vrfHelper("fmovies-vrf", movieid)

        val title = soup.selectFirst(".film-title")?.text() ?: ""
        val description = soup.selectFirst(".film-desc")?.text()?.trim()
        val poster = soup.selectFirst("#film-detail .film-poster img")?.attr("src") ?: soup.selectFirst(".info .poster img")?.attr("src")

        val backimginfo = (soup.selectFirst(".film-background") ?: soup.selectFirst(".player-bg"))?.attr("style")
        val backimgRegx = Regex("(http|https).*jpg")
        val backposter = backimgRegx.find(backimginfo.toString())?.value ?: poster
        val tags = soup.select(".film-info .film-meta div:contains(Genre) a").map { it.text() }

        val vrfUrl = "$mainUrl/ajax/episode/list/$movieid?vrf=$movieidencoded"
        val episodes = ArrayList<Episode>()
        val asse = Jsoup.parse(app.get(vrfUrl).parsed<Response>().result)
        var dataMovie = ""
        
        asse.select("ul.episodes").forEach { main ->
            val seasonNum = main.attr("data-season").toIntOrNull()
            main.select("li a").forEach { sec ->
                val epID = sec.attr("data-id")
                val epNum = sec.attr("data-slug").toIntOrNull()
                val epTitle = sec.text().replace(Regex("(Episode (\\d+):|Episode (\\d+)-|Episode (\\d+))"), "").trim()
                episodes.add(
                    newEpisode(epID) {
                        this.name = epTitle
                        this.season = seasonNum
                        this.episode = epNum
                    }
                )
            }
        }

        val tvType = if (url.contains("/movie/") && episodes.size == 1) TvType.Movie else TvType.TvSeries

        if (tvType == TvType.Movie) {
            dataMovie = asse.select("ul.episodes li a").attr("data-id")
        }

        val scoreValue = Score.from10(soup.selectFirst("div.score.live-label span b[itemprop=ratingValue]")?.text())
        val durationdoc = (soup.selectFirst("div.meta > span:nth-child(4)") ?: soup.selectFirst("div.meta > span:nth-child(5)"))?.text() ?: ""
        
        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backposter
                this.plot = description
                this.score = scoreValue
                this.tags = tags
                addDuration(durationdoc)
            }
        } else {
            newMovieLoadResponse(title, url, tvType, dataMovie) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backposter
                this.plot = description
                this.score = scoreValue
                this.tags = tags
                addDuration(durationdoc)
            }
        }
    }

    data class Subtitles(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("kind") val kind: String
    )

    data class Result(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("rawURL") val rawURL: String? = null
    )

    data class Links(
        @JsonProperty("result") val result: Result? = null
    )

    private fun serverName(serverID: String?): String? {
        return when (serverID) {
            "28" -> "mcloud"
            "41" -> "vidplay"
            "45" -> "filemoon"
            "40" -> "streamtape"
            "35" -> "mp4upload"
            else -> null
        }
    }

    data class BflixMediaInfo (
        @JsonProperty("result" ) val result : BflixResult? = BflixResult()
    )

    data class BflixResult (
        @JsonProperty("sources" ) var sources : ArrayList<BflixTracks> = arrayListOf(),
        @JsonProperty("tracks"  ) var tracks  : ArrayList<BflixTracks>  = arrayListOf()
    )
    
    data class BflixTracks (
        @JsonProperty("file"    ) var file    : String?  = null,
        @JsonProperty("label"   ) var label   : String?  = null,
    )

    class TestingSubs : ArrayList<TestingSubsItem>()

    data class TestingSubsItem(
        @JsonProperty("file"    ) var file    : String?  = null,
        @JsonProperty("label"   ) var label   : String?  = null,
        @JsonProperty("kind"    ) var kind    : String?  = null,
        @JsonProperty("default" ) var default : Boolean? = null
    )
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val dataEncoded = encode(vrfHelper("fmovies-vrf", data))
        val serversUrl = "$mainUrl/ajax/server/list/$data?vrf=$dataEncoded"
        val serversPair = Jsoup.parse(app.get(serversUrl).parsed<Response>().result).select(".film-server").map {
            val serverId = it.attr("data-id")
            val datalinkId = it.attr("data-link-id")
            val newSname = serverName(serverId)
            Pair(newSname, datalinkId)
        }

        serversPair.forEach {(sName, sId) ->
            val encId = vrfHelper("fmovies-vrf", sId)
            val urlEnc = app.get("$mainUrl/ajax/server/$sId?vrf=$encId").parsed<Links>().result?.url

            if (urlEnc != null) {
                val vids = sName == "vidplay"
                val mclo = sName == "mcloud"
                if (vids || mclo) {
                    val decUrl = vrfHelper("fmovies-decrypt", urlEnc)
                    val futoken = app.get("https://vidstream.pro/futoken").text
                    val vizId = decUrl.substringAfterLast("/")
                    val map = mapOf("query" to vizId, "futoken" to futoken)
                    val jsonBody = JSONObject(map).toString()
                    val action = if (vids) "rawVizcloud" else "rawMcloud"
                    val mediaUrl = app.post("https://9anime.eltik.net/$action?apikey=lagrapps", requestBody = jsonBody.toRequestBody(mediaType)).parsed<Result>().rawURL
                    val ref = if (vids) "https://vidstream.pro/" else "https://mcloud.to/"
                    if (mediaUrl != null) {
                        val resultJson = app.get(mediaUrl, headers = mapOf("Referer" to ref)).parsed<BflixMediaInfo>()
                        val name = if (vids) "Vidplay" else "MyCloud"
                        resultJson.result?.sources?.forEach {
                            val source = it.file ?: ""
                            M3u8Helper.generateM3u8(name, source, ref).forEach(callback)
                        }
                    }
                }
                if (!sName.isNullOrEmpty() && !vids && !mclo) {
                    val decUrl = vrfHelper("fmovies-decrypt", urlEnc)
                    loadExtractor(decUrl, subtitleCallback, callback)
                }
            }
        }
        
        runCatching {
            val subsUrl = app.get("$mainUrl/ajax/episode/subtitles/$data").parsed<TestingSubs>()
            subsUrl.forEach {
                val subtitle = it.file ?: return@forEach
                val lang = it.label ?: ""
                subtitleCallback.invoke(SubtitleFile(lang, subtitle))
            }
        }
        return true
    }
}
