package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class GogoanimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

        private fun getKey(id: String): String? {
            return try {
                id.map {
                    it.code.toString(16)
                }.joinToString("").substring(0, 32)
            } catch (e: Exception) {
                null
            }
        }

        val qualityRegex = Regex("(\\d+)P")

        private fun cryptoHandler(
            string: String,
            iv: String,
            secretKeyString: String,
            encrypt: Boolean = true
        ): String {
            val ivParameterSpec = IvParameterSpec(iv.toByteArray())
            val secretKey = SecretKeySpec(secretKeyString.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            return if (!encrypt) {
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
                String(cipher.doFinal(base64DecodeArray(string)))
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
                base64Encode(cipher.doFinal(string.toByteArray()))
            }
        }

        suspend fun extractVidstream(
            iframeUrl: String,
            mainApiName: String,
            callback: (ExtractorLink) -> Unit,
            iv: String?,
            secretKey: String?,
            secretDecryptKey: String?,
            isUsingAdaptiveKeys: Boolean,
            isUsingAdaptiveData: Boolean,
            iframeDocument: Document? = null
        ) {
            runCatching {
                if ((iv == null || secretKey == null || secretDecryptKey == null) && !isUsingAdaptiveKeys)
                    return@runCatching

                val id = Regex("id=([^&]+)").find(iframeUrl)?.groupValues?.get(1) ?: return@runCatching

                var document: Document? = iframeDocument
                val foundIv = iv ?: (document ?: app.get(iframeUrl).document.also { document = it })
                    .select("""div.wrapper[class*=container]""")
                    .attr("class").split("-").lastOrNull() ?: return@runCatching
                val foundKey = secretKey ?: getKey(base64Decode(id) + foundIv) ?: return@runCatching
                val foundDecryptKey = secretDecryptKey ?: foundKey

                val uri = URI(iframeUrl)
                val mainUrl = "https://" + uri.host

                val encryptedId = cryptoHandler(id, foundIv, foundKey)
                val encryptRequestData = if (isUsingAdaptiveData) {
                    val realDocument = document ?: app.get(iframeUrl).document
                    val dataEncrypted = realDocument.select("script[data-name='episode']").attr("data-value")
                    val headers = cryptoHandler(dataEncrypted, foundIv, foundKey, false)
                    "id=$encryptedId&alias=$id&" + headers.substringAfter("&")
                } else {
                    "id=$encryptedId&alias=$id"
                }

                val jsonResponse = app.get(
                    "$mainUrl/encrypt-ajax.php?$encryptRequestData",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                )
                val dataencrypted = jsonResponse.text.substringAfter("{\"data\":\"").substringBefore("\"}")
                val datadecrypted = cryptoHandler(dataencrypted, foundIv, foundDecryptKey, false)
                val sources = AppUtils.parseJson<GogoSources>(datadecrypted)

                sources.source?.forEach {
                    callback.invoke(
                        newExtractorLink(
                            mainApiName,
                            mainApiName,
                            it.file,
                            INFER_TYPE
                        ) {
                            this.referer = mainUrl
                            this.quality = getQualityFromName(it.label)
                        }
                    )
                }
                sources.sourceBk?.forEach {
                    callback.invoke(
                        newExtractorLink(
                            mainApiName,
                            mainApiName,
                            it.file,
                            INFER_TYPE
                        ) {
                            this.referer = mainUrl
                            this.quality = getQualityFromName(it.label)
                        }
                    )
                }
            }
        }
    }

    override var mainUrl = "https://gogoanime.lu"
    override var name = "GogoAnime"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    val customHeaders = mapOf(
        "authority" to "ajax.gogo-load.com",
        "sec-ch-ua" to "\"Google Chrome\";v=\"89\", \"Chromium\";v=\"89\", \";Not A Brand\";v=\"99\"",
        "accept" to "text/html, */*; q=0.01",
        "dnt" to "1",
        "sec-ch-ua-mobile" to "?0",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36",
        "origin" to mainUrl,
        "sec-fetch-site" to "cross-site",
        "sec-fetch-mode" to "cors",
        "sec-fetch-dest" to "empty",
        "referer" to "$mainUrl/"
    )
    val parseRegex =
        Regex("""<li>\s*\n.*\n.*<a\s*href=["'](.*?-episode-(\d+))["']\s*title=["'](.*?)["']>\n.*?img src="(.*?)"""")

    override val mainPage = mainPageOf(
        "1" to "Recent Release - Sub",
        "2" to "Recent Release - Dub",
        "3" to "Recent Release - Chinese",
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val params = mapOf("page" to page.toString(), "type" to request.data)
        val html = app.get(
            "https://ajax.gogo-load.com/ajax/page-recent-release.html",
            headers = customHeaders,
            params = params
        )
        val isSub = listOf(1, 3).contains(request.data.toInt())

        val home = parseRegex.findAll(html.text).map {
            val (link, epNum, title, poster) = it.destructured
            newAnimeSearchResponse(title, link, TvType.Anime) {
                this.posterUrl = poster
                addDubStatus(!isSub, epNum.toIntOrNull())
            }
        }.toList()

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search.html?keyword=$query"
        val html = app.get(link).text
        val doc = Jsoup.parse(html)

        return doc.select(""".last_episodes li""").mapNotNull {
            val title = it.selectFirst(".name")?.text()?.replace(" (Dub)", "") ?: return@mapNotNull null
            val url = fixUrl(it.selectFirst(".name > a")?.attr("href") ?: return@mapNotNull null)
            val isDub = it.selectFirst(".name")?.text()?.contains("Dub") == true
            newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = it.selectFirst("img")?.attr("src")
                addDubStatus(isDub, !isDub)
            }
        }
    }

    private fun getProperAnimeLink(uri: String): String {
        if (uri.contains("-episode")) {
            val split = uri.split("/")
            val slug = split[split.size - 1].split("-episode")[0]
            return "$mainUrl/category/$slug"
        }
        return uri
    }

    override suspend fun load(url: String): LoadResponse {
        val link = getProperAnimeLink(url)
        val episodeloadApi = "https://ajax.gogo-load.com/ajax/load-list-episode"
        val doc = app.get(link).document

        val animeBody = doc.selectFirst(".anime_info_body_bg")
        val title = animeBody?.selectFirst("h1")?.text() ?: ""
        val poster = animeBody?.selectFirst("img")?.attr("src")
        var description: String? = null
        val genre = ArrayList<String>()
        var year: Int? = null
        var status: String? = null
        var nativeName: String? = null
        var type: String? = null

        animeBody?.select("p.type")?.forEach { pType ->
            when (pType.selectFirst("span")?.text()?.trim()) {
                "Plot Summary:" -> {
                    description = pType.text().replace("Plot Summary:", "").trim()
                }
                "Genre:" -> {
                    genre.addAll(pType.select("a").map { it.attr("title") })
                }
                "Released:" -> {
                    year = pType.text().replace("Released:", "").trim().toIntOrNull()
                }
                "Status:" -> {
                    status = pType.text().replace("Status:", "").trim()
                }
                "Other name:" -> {
                    nativeName = pType.text().replace("Other name:", "").trim()
                }
                "Type:" -> {
                    type = pType.text().replace("type:", "").trim()
                }
            }
        }

        val animeId = doc.selectFirst("#movie_id")?.attr("value") ?: ""
        val params = mapOf("ep_start" to "0", "ep_end" to "2000", "id" to animeId)

        val episodes = app.get(episodeloadApi, params = params).document.select("a").map {
            val epName = it.selectFirst(".name")?.text()?.replace("EP", "")?.trim()
            newEpisode(fixUrl(it.attr("href").trim())) {
                this.name = "Episode $epName"
            }
        }.reversed()

        return newAnimeLoadResponse(title, link, getType(type.toString())) {
            japName = nativeName
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            plot = description
            tags = genre
            showStatus = getStatus(status.toString())
        }
    }

    data class GogoSources(
        @JsonProperty("source") val source: List<GogoSource>?,
        @JsonProperty("sourceBk") val sourceBk: List<GogoSource>?,
    )

    data class GogoSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("default") val default: String? = null
    )

    private suspend fun extractVideos(
        uri: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(uri).document
        val iframe = fixUrlNull(doc.selectFirst("div.play-video > iframe")?.attr("src")) ?: return

        runAllAsync(
            {
                val link = iframe.replace("streaming.php", "download")
                val page = app.get(link, headers = mapOf("Referer" to iframe))

                page.document.select(".dowload > a").forEach {
                    if (it.hasAttr("download")) {
                        val qual = if (it.text().contains("HDP")) "1080" else qualityRegex.find(it.text())?.groupValues?.get(1) ?: ""
                        callback(
                            newExtractorLink(
                                "Gogoanime",
                                "Gogoanime",
                                it.attr("href"),
                                INFER_TYPE
                            ) {
                                this.referer = page.url
                                this.quality = getQualityFromName(qual)
                            }
                        )
                    } else {
                        val url = it.attr("href")
                        loadExtractor(url, null, subtitleCallback, callback)
                    }
                }
            }, {
                val streamingResponse = app.get(iframe, headers = mapOf("Referer" to iframe))
                val streamingDocument = streamingResponse.document
                runAllAsync({
                    streamingDocument.select(".list-server-items > .linkserver")
                        .forEach { element ->
                            val status = element.attr("data-status") ?: return@forEach
                            if (status != "1") return@forEach
                            val data = element.attr("data-video") ?: return@forEach
                            loadExtractor(data, streamingResponse.url, subtitleCallback, callback)
                        }
                }, {
                    val iv = "3134003223491201"
                    val secretKey = "37911490979715163134003223491201"
                    val secretDecryptKey = "54674138327930866480207815084989"
                    extractVidstream(
                        iframe,
                        this.name,
                        callback,
                        iv,
                        secretKey,
                        secretDecryptKey,
                        isUsingAdaptiveKeys = false,
                        isUsingAdaptiveData = true
                    )
                })
            }
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        extractVideos(data, subtitleCallback, callback)
        return true
    }
}
