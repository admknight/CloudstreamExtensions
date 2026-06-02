package com.admknight.showflix

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.json.JSONObject
import java.net.URI

open class StreamRuby : VidhideExtractor() {
    override var name = "StreamRuby"
    override var mainUrl = "https://streamruby.com"
    override val requiresReferer = false
}

class Showflixupnshare : VidStack() {
    override var name: String = "VidStack"
    override var mainUrl: String = "https://showflix.upns.one"
}

class Rubyvidhub : VidhideExtractor() {
    override var mainUrl = "https://rubyvidhub.com"
}

class Smoothpre : VidhideExtractor() {
    override var mainUrl = "https://smoothpre.com"
    override var requiresReferer = true
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

class Showflixarchives : ExtractorApi() {
    override val name = "Showflix Archives"
    override val mainUrl = "https://showflix.sbs"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = app.get(url).document.select("a[href]")
        for (link in links) {
            val href = link.attr("href")
            if ("gdflix" in href || "appdrive" in href || "gdlink" in href) {
                if (href.contains("gdflix", ignoreCase = true) || href.contains("gdlink", ignoreCase = true)) {
                    GDFlix().getUrl(href, referer, subtitleCallback, callback)
                } else {
                    Driveseed().getUrl(href, referer, subtitleCallback, callback)
                }
            }
        }
    }
}

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new6.gdflix.dad"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = try {
            app.get(url).document.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=") ?: url
        } catch (e: Exception) { url }

        val document = app.get(newUrl).document
        val fileName = document.select("ul > li.list-group-item:contains(Name)").text().substringAfter("Name : ")
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text().substringAfter("Size : ")

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.text()
            when {
                text.contains("DIRECT DL", true) -> {
                    callback.invoke(newExtractorLink("$source GDFlix[Direct]", "$source GDFlix[Direct] [$fileSize]", anchor.attr("href"), INFER_TYPE) {
                        this.quality = getIndexQuality(fileName)
                    })
                }
                text.contains("Index Links", true) -> {
                    runCatching {
                        val link = anchor.attr("href")
                        app.get("https://new6.gdflix.dad$link").document.select("a.btn.btn-outline-info").amap { btn ->
                            app.get("https://new6.gdflix.dad" + btn.attr("href")).document.select("div.mb-4 > a").amap { sAnchor ->
                                callback.invoke(newExtractorLink("$source GDFlix[Index]", "$source GDFlix[Index] [$fileSize]", sAnchor.attr("href"), INFER_TYPE) {
                                    this.quality = getIndexQuality(fileName)
                                })
                            }
                        }
                    }
                }
                text.contains("Instant DL", true) -> {
                    runCatching {
                        val link = app.get(anchor.attr("href"), allowRedirects = false).headers["location"]?.substringAfter("url=") ?: ""
                        if (link.isNotBlank()) {
                            callback.invoke(newExtractorLink("$source GDFlix[Instant Download]", "$source GDFlix[Instant Download] [$fileSize]", link, INFER_TYPE) {
                                this.quality = getIndexQuality(fileName)
                            })
                        }
                    }
                }
                text.contains("CLOUD DOWNLOAD", true) -> {
                    callback.invoke(newExtractorLink("$source GDFlix[CLOUD]", "$source GDFlix[CLOUD] [$fileSize]", anchor.attr("href"), INFER_TYPE) {
                        this.quality = getIndexQuality(fileName)
                    })
                }
                text.contains("GoFile", true) -> {
                    runCatching {
                        app.get(anchor.attr("href")).document.select(".row .row a").amap { gAnchor ->
                            if (gAnchor.attr("href").contains("gofile")) Gofile().getUrl(gAnchor.attr("href"), "", subtitleCallback, callback)
                        }
                    }
                }
                text.contains("Pixel", true) -> {
                    callback.invoke(newExtractorLink("$source GDFlix[Pixeldrain]", "$source GDFlix[Pixeldrain] [$fileSize]", anchor.attr("href"), INFER_TYPE))
                }
            }
        }

        runCatching {
            listOf("type=1", "type=2").forEach { t ->
                val sUrl = app.get("${newUrl.replace("file", "wfile")}?$t").document.selectFirst("a.btn-success")?.attr("href")
                if (sUrl != null) {
                    callback.invoke(newExtractorLink("$source GDFlix[CF]", "$source GDFlix[CF] [$fileSize]", sUrl, INFER_TYPE) {
                        this.quality = getIndexQuality(fileName)
                    })
                }
            }
        }
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

open class Driveseed : ExtractorApi() {
    override val name: String = "Driveseed"
    override val mainUrl: String = "https://driveseed.org"
    override val requiresReferer = false

    private suspend fun instantLink(finalLink: String): String? {
        return runCatching {
            val uri = URI(finalLink)
            val host = uri.host ?: "video-leech.pro"
            val token = finalLink.substringAfter("url=")
            val res = app.post("https://$host/api", data = mapOf("keys" to token), referer = finalLink, headers = mapOf("x-token" to host)).text
            res.substringAfter("url\":\"").substringBefore("\",\"name").replace("\\/", "/").takeIf { it.startsWith("http") }
        }.getOrNull()
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val base = URI(url).let { "${it.scheme}://${it.host}" }
        val doc = try {
            if (url.contains("r?key=")) {
                val path = app.get(url).document.selectFirst("script")?.data()?.substringAfter("replace(\"")?.substringBefore("\")") ?: ""
                app.get(mainUrl + path).document
            } else app.get(url).document
        } catch (e: Exception) { return }

        val qText = doc.selectFirst("li.list-group-item")?.text() ?: ""
        val fName = qText.removePrefix("Name : ").trim()
        val size = doc.select("li.list-group-item").getOrNull(2)?.text()?.removePrefix("Size : ")?.trim() ?: ""
        val label = "[$fName][$size]"

        doc.select("div.text-center > a").forEach { el ->
            val text = el.text()
            val href = el.attr("href")
            if (href.isBlank()) return@forEach
            when {
                text.contains("Instant Download", true) -> {
                    instantLink(href)?.let { link ->
                        callback(newExtractorLink("$name Instant $label", "$name Instant $label", link, INFER_TYPE) { this.quality = getIndexQuality(qText) })
                    }
                }
                text.contains("Direct Links", true) -> {
                    runCatching {
                        app.get("$base$href?type=1").document.select("a.btn-success").forEach { link ->
                            callback(newExtractorLink("$name CF Type1 $label", "$name CF Type1 $label", link.attr("href"), INFER_TYPE) { this.quality = getIndexQuality(qText) })
                        }
                    }
                }
            }
        }
    }
}
