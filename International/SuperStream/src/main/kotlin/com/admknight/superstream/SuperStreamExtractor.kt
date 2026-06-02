package com.admknight.superstream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.*
import com.admknight.superstream.BuildConfig.SUPERSTREAM_FOURTH_API
import com.admknight.superstream.BuildConfig.SUPERSTREAM_THIRD_API
import com.admknight.superstream.BuildConfig.NuvFeb
import org.jsoup.Jsoup
import java.net.URLEncoder

object SuperStreamExtractor {
    private val headers = mapOf("User-Agent" to "Mozilla/5.0")

    suspend fun invokeSuperstream(
        linkData: SuperStream.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = linkData.id ?: return
        val type = if (linkData.season == null) 1 else 2
        val season = linkData.season
        val episode = linkData.episode
        
        val url = "$SUPERSTREAM_THIRD_API/file/file_share_list?share_key=A9mKzS0Y&parent_id=$id&page=1"
        val res = app.get(url, headers = headers).parsedSafe<ExternalResponse>()
        res?.data?.fileList?.forEach { file ->
            val linkRes = app.get("$SUPERSTREAM_THIRD_API/file/player?fid=${file.fid}&share_key=A9mKzS0Y", headers = headers).parsedSafe<ER>()
            linkRes?.data?.link?.let { link ->
                callback(newExtractorLink("SuperStream", file.fileName ?: "File", link, INFER_TYPE))
            }
        }
    }

    suspend fun invokeSuperstreamFeb(
        fid: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = "$NuvFeb/file/file_share_list?share_key=A9mKzS0Y&parent_id=$fid&page=1"
        val res = app.get(url, headers = headers).parsedSafe<ExternalResponse>()
        res?.data?.fileList?.forEach { file ->
            val linkRes = app.get("$NuvFeb/file/player?fid=${file.fid}&share_key=A9mKzS0Y", headers = headers).parsedSafe<ER>()
            linkRes?.data?.link?.let { link ->
                callback(newExtractorLink("SuperStream Feb", file.fileName ?: "File", link, INFER_TYPE))
            }
        }
    }
}
