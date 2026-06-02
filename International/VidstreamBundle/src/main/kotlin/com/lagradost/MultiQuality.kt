package com.lagradost

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.INFER_TYPE
import java.net.URI

class MultiQuality : ExtractorApi() {
    override var name = "MultiQuality"
    override var mainUrl = "https://gogo-play.net"
    private val sourceRegex = Regex("""file:\s*['"](.*?)['"],label:\s*['"](.*?)['"]""")
    private val m3u8Regex = Regex(""".*?(\d*).m3u8""")
    private val urlRegex = Regex("""(.*?)([^/]+$)""")
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/loadserver.php?id=$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val response = app.get(url)
        sourceRegex.findAll(response.text).forEach { sourceMatch ->
            val extractedUrl = sourceMatch.groupValues[1]
            if (URI(extractedUrl).path.endsWith(".m3u8")) {
                val m3u8Response = app.get(extractedUrl)
                m3u8Regex.findAll(m3u8Response.text).forEach { match ->
                    extractedLinksList.add(
                        newExtractorLink(name, name, urlRegex.find(m3u8Response.url)!!.groupValues[1] + match.groupValues[0], INFER_TYPE) {
                            this.quality = getQualityFromName(match.groupValues[1])
                            this.referer = url
                        }
                    )
                }
            } else if (extractedUrl.endsWith(".mp4")) {
                extractedLinksList.add(
                    newExtractorLink(name, "$name ${sourceMatch.groupValues[2]}", extractedUrl, INFER_TYPE) {
                        this.referer = url.replace(" ", "%20")
                    }
                )
            }
        }
        return extractedLinksList
    }
}
