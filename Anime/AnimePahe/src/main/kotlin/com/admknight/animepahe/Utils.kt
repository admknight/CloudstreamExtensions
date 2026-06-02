package com.admknight.animepahe

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI

suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    link.name,
                    link.url,
                    INFER_TYPE
                ) {
                    this.quality = quality ?: link.quality
                    this.referer = link.referer
                    this.headers = link.headers
                }
            )
        }
    }
}

class Kwik : ExtractorApi() {
    override val name            = "Kwik"
    override val mainUrl         = "https://kwik.cx"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val res = app.get(url, referer = "${AnimePaheProviderPlugin.currentAnimepaheServer}/")
        val unpacked = getAndUnpack(res.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data() ?: return)
        val m3u8 = Regex("source=\\s*'(.*?m3u8.*?)'").find(unpacked)?.groupValues?.get(1) ?: ""
        val fileName = res.document.title().substringBeforeLast(".mp4") + ".mp4"
        val mp4Url = m3u8.replace("/stream/", "/mp4/").substringBeforeLast("/") + "?file=${java.net.URLEncoder.encode(fileName, "UTF-8")}"

        callback.invoke(newExtractorLink(name, name, m3u8, INFER_TYPE) {
            this.referer = mainUrl
            this.quality = getQualityFromName(fileName)
        })

        callback(newExtractorLink(name, "$name [Download]", mp4Url, ExtractorLinkType.VIDEO) {
            this.referer = url
            this.quality = getQualityFromName(fileName)
        })
    }
}

class Pahe : ExtractorApi() {
    override val name = "Pahe"
    override val mainUrl = "https://pahe.win"
    override val requiresReferer = true
    private val client = OkHttpClient()

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val initialUrl = noRedirects.newCall(Request.Builder().url("$url/i").get().build()).execute().header("location") ?: return
        
        val fContent = client.newCall(Request.Builder().url(initialUrl).header("referer", "https://kwik.cx/").get().build()).execute()
        val fContentString = fContent.body?.string() ?: ""
        val match = Regex("""\("(\w+)",\d+,"(\w+)",(\d+),(\d+),\d+\)""").find(fContentString) ?: return
        val (fullString, key, v1, v2) = match.destructured
        
        val decrypted = decryptPahe(fullString, key, v1.toInt(), v2.toInt())
        val uri = Regex("action=\"([^\"]+)\"").find(decrypted)?.groupValues?.get(1) ?: return
        val tok = Regex("value=\"([^\"]+)\"").find(decrypted)?.groupValues?.get(1) ?: return

        var code = 419
        var tries = 0
        var content: Response? = null
        while (code != 302 && tries < 20) {
            val req = Request.Builder().url(uri).post(FormBody.Builder().add("_token", tok).build()).header("referer", initialUrl).build()
            content = noRedirects.newCall(req).execute()
            code = content.code
            tries++
        }
        val location = content?.header("location") ?: ""
        content?.close()

        if (location.isNotBlank()) {
            callback.invoke(newExtractorLink(name, name, location, INFER_TYPE) {
                this.referer = "https://kwik.cx/"
            })
        }
    }

    private fun decryptPahe(fullString: String, key: String, v1: Int, v2: Int): String {
        val keyIndexMap = key.withIndex().associate { it.value to it.index }
        val sb = StringBuilder()
        var i = 0
        val toFind = key[v2]
        while (i < fullString.length) {
            val nextIndex = fullString.indexOf(toFind, i)
            val decodedCharStr = fullString.substring(i, nextIndex).map { keyIndexMap[it] ?: -1 }.joinToString("")
            i = nextIndex + 1
            sb.append((decodedCharStr.toInt(v2) - v1).toChar())
        }
        return sb.toString()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaImage(@JsonProperty("coverType") val coverType: String?, @JsonProperty("url") val url: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisode(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("runtime") val runtime: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @JsonProperty("images") val images: List<MetaImage>?,
    @JsonProperty("episodes") val episodes: Map<String, MetaEpisode>?
)

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try { ObjectMapper().readValue(jsonString, MetaAnimeData::class.java) } catch (_: Exception) { null }
}
