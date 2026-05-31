package com.admknight.tokusatsuultimate

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TokusatsuUltimate : MainAPI() {
    override var mainUrl = "https://toku555.com"
    override var name = "TokusatsuUltimate"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.TvSeries, TvType.Movie)

    companion object {
        fun cleanTitle(title: String): String {
            var cleaned = title
            val suffixes = listOf(" - Tokusatsu", " | Tokusatsu", " | Official")
            for (suffix in suffixes) {
                if (cleaned.endsWith(suffix)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length)
                }
            }
            return cleaned.trim()
        }
    }

    override val mainPage = mainPageOf(
        "kamen-rider" to "Kamen Rider Series",
        "super-sentai" to "Super Sentai Series",
        "tokusatsu-anime" to "Tokusatsu Anime",
        "metal-heroes" to "Metal Heroes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${mainUrl}/${request.data}/page/$page/"
        val document = app.get(url).document
        val home = document.select("div.film-poster, .item, .series-item").mapNotNull { element ->
            val titleElement = element.selectFirst(".film-title a, .title a, h3 a") ?: element.selectFirst("a")
            if (titleElement != null) {
                val title = cleanTitle(titleElement.text().trim())
                val href = fixUrl(titleElement.attr("href"))
                val posterElement = element.selectFirst(".film-poster img, img")
                val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

                newAnimeSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = posterUrl
                }
            } else null
        }
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/${query}/"
        val document = app.get(searchUrl).document
        return document.select("div.film-poster, .item, .series-item").mapNotNull { element ->
            val titleElement = element.selectFirst(".film-title a, .title a, h3 a") ?: return@mapNotNull null
            val title = cleanTitle(titleElement.text().trim())
            val href = fixUrl(titleElement.attr("href"))
            val posterElement = element.selectFirst(".film-poster img, img")
            val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val titleElement = document.selectFirst("h1, .film-name, .title") ?: throw ErrorLoadingException("No title found")
        val title = cleanTitle(titleElement.text().trim())
        val posterElement = document.selectFirst(".film-poster img, .poster img, img[src*='image']")
        val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))
        val year = document.selectFirst(".year, .date, .released")?.text()?.trim()?.toIntOrNull()
        val description = document.selectFirst("div.description, .content")?.text()?.trim()
        val tags = document.select(".genres a, .tags a, .category a").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        document.select("ul.pagination.post-tape li").amap { epElement ->
            val epA = epElement.selectFirst("a") ?: return@amap
            val epTitle = epA.text().trim()
            val epHref = epA.attr("href")
            val epDoc = app.get(epHref).document
            val iframeSrc = epDoc.selectFirst("div.player iframe")?.attr("src") ?: ""
            if (iframeSrc.isNotEmpty()) {
                episodes.add(newEpisode(iframeSrc) {
                    this.name = "Episode $epTitle"
                    this.episode = epTitle.toIntOrNull()
                })
            }
        }

        if (episodes.isEmpty()) {
            val iframe = document.selectFirst("div.player iframe")?.attr("src") ?: ""
            return newMovieLoadResponse(title, url, TvType.Movie, iframe) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.tags = tags
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = posterUrl
            this.year = year
            this.plot = description
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes.reversed())
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        loadExtractor(data, data, subtitleCallback, callback)
        return true
    }

    open class VidStack : ExtractorApi() {
        override var name = "Vidstack"
        override var mainUrl = "https://vidstack.io"
        override val requiresReferer = true

        override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
            val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) rv:134.0) Gecko/20100101 Firefox/134.0")
            val hash = url.substringAfterLast("#").substringAfter("/")
            val baseurl = try { URI(url).let { "${it.scheme}://${it.host}" } } catch(e: Exception) { mainUrl }
            val encoded = app.get("$baseurl/api/v1/video?id=$hash", headers = headers).text.trim()

            val key = "kiemtienmua911ca"
            val ivList = listOf("1234567890oiuytr", "0123456789abcdef")

            val decryptedText = ivList.firstNotNullOfOrNull { iv ->
                try { AesHelper.decryptAES(encoded, key, iv) } catch (e: Exception) { null }
            } ?: throw Exception("Failed to decrypt")

            val m3u8 = Regex("\"source\":\"(.*?)\"").find(decryptedText)?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
            
            val subtitlePattern = Regex("\"([^\"]+)\":\\s*\"([^\"]+)\"")
            val subtitleSection = Regex("\"subtitle\":\\{(.*?)\\}").find(decryptedText)?.groupValues?.get(1)
            subtitleSection?.let { section ->
                subtitlePattern.findAll(section).forEach { match ->
                    val lang = match.groupValues[1]
                    val path = match.groupValues[2].split("#")[0].replace("\\/", "/")
                    if (path.isNotEmpty()) {
                        subtitleCallback(newSubtitleFile(lang, fixUrl("$mainUrl$path")))
                    }
                }
            }

            callback.invoke(newExtractorLink(this.name, this.name, m3u8, type = ExtractorLinkType.M3U8) {
                this.referer = url
                this.headers = mapOf("referer" to url, "Origin" to url.substringAfterLast("/"))
                this.quality = Qualities.Unknown.value
            })
        }
    }

    object AesHelper {
        private const val TRANSFORMATION = "AES/CBC/PKCS5PADDING"
        fun decryptAES(inputHex: String, key: String, iv: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = SecretKeySpec(key.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(iv.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            return String(cipher.doFinal(inputHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()))
        }
    }
}



