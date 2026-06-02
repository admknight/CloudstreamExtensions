package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class AsianEmbedHelper {
    companion object {
        suspend fun getUrls(
            url: String,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            val doc = app.get(url).document
            val links = doc.select("div#list-server-more > ul > li.linkserver")
            links.forEach {
                val dataVid = it.attr("data-video")
                if (dataVid.isNotBlank()) {
                    val res = loadExtractor(dataVid, url, subtitleCallback, callback)
                    Log.i("AsianEmbed", "Result => ($res) (datavid) $dataVid")
                }
            }
        }
    }
}
