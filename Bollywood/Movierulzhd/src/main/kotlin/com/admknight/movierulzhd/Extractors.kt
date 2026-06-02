package com.admknight.movierulzhd

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URI

class FMHD : Filesim() {
    override val name = "FMHD"
    override var mainUrl = "https://fmhd.bar/"
    override val requiresReferer = true
}

class Playonion : Filesim() {
    override val mainUrl = "https://playonion.sbs"
}

class Luluvdo : StreamWishExtractor() {
    override val mainUrl = "https://luluvdo.com"
}

class Lulust : StreamWishExtractor() {
    override val mainUrl = "https://lulu.st"
}

class Movierulz : FilemoonV2() {
    override var name = "Movierulz"
    override var mainUrl = "https://movierulz2025.bar"
}

class Movierulzups : VidStack() {
    override var name = "Movierulz"
    override var mainUrl = "https://onion.uns.wtf"
}

class cherryMovierulzups : VidStack() {
    override var name = "Movierulz"
    override var mainUrl = "https://cherry.upns.online"
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

open class FMX : ExtractorApi() {
    override var name = "FMX"
    override var mainUrl = "https://fmx.lol"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = mainUrl).document
        val script = response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data() ?: return null
        JsUnpacker(script).unpack()?.let { unpacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unpacked)?.groupValues?.get(1)?.let { link ->
                return listOf(newExtractorLink(this.name, this.name, link, INFER_TYPE) {
                    this.referer = referer ?: ""
                })
            }
        }
        return null
    }
}

open class Akamaicdn : ExtractorApi() {
    override val name = "Akamaicdn"
    override val mainUrl = "https://molop.art"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf("user-agent" to "okhttp/4.12.0")
        val res = app.get(url, referer = referer, headers = headers).document
        val sniffScript = res.selectFirst("script:containsData(sniff\\()")?.data()?.substringAfter("sniff(")?.substringBefore(");") ?: return
        
        val regex = Regex("\"(.*?)\"")
        val args = regex.findAll(sniffScript).map { it.groupValues[1].trim() }.toList()
        val token = args.lastOrNull().orEmpty()
        val m3u8 = "$mainUrl/m3u8/${args.getOrNull(1)}/${args.getOrNull(2)}/master.txt?s=1&cache=1&plt=$token"
        M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = headers).forEach(callback)
    }
}

class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        runCatching {
            val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return@runCatching
            val token = JSONObject(app.post("$mainApi/accounts").text).getJSONObject("data").getString("token")
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""").find(app.get("$mainUrl/dist/js/global.js").text)?.groupValues?.get(1) ?: return@runCatching
            val fileJson = JSONObject(app.get("$mainApi/contents/$id?wt=$wt", headers = mapOf("Authorization" to "Bearer $token")).text).getJSONObject("data")
            val children = fileJson.getJSONObject("children")
            val fileObj = children.getJSONObject(children.keys().next())
            val link = fileObj.getString("link")
            val fName = fileObj.getString("name")
            val size = fileObj.getLong("size")
            val sizeStr = if (size < 1073741824L) "%.2f MB".format(size / 1048576.0) else "%.2f GB".format(size / 1073741824.0)

            callback.invoke(newExtractorLink("Gofile", "Gofile [$sizeStr]", link, INFER_TYPE) {
                this.quality = getIndexQuality(fName)
                this.headers = mapOf("Cookie" to "accountToken=$token")
            })
        }
    }
}
