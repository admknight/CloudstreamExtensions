package com.admknight.movierulzhd

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.util.Calendar

open class Hdmovie2 : Movierulzhd() {
    override var mainUrl: String = runBlocking {
        MovierulzhdPlugin.getDomains()?.movierulzhd ?: "https://hdmovie2.qpon"
    }
    override var name = "Hdmovie2"
    override val mainPage = mainPageOf(
        "release/${Calendar.getInstance().get(Calendar.YEAR)}" to "Latest",
        "genre/bollywood" to "BollyWood",
        "movies" to "Movies",
        "genre/hindi-webseries" to "Hindi Web Series",
        "genre/netflix" to "Netflix",
        "genre/zee5" to "Zee5",
        "genre/hindi-dubbed" to "Hindi Dubbed",
        "genre/comedy" to "Comedy",
        "genre/science-fiction" to "Science Fiction"
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
        
        suspend fun fetchSource(post: String, nume: String, type: String): String {
            val res = app.post(
                url = ajaxUrl,
                data = mapOf("action" to "doo_player_ajax", "post" to post, "nume" to nume, "type" to type),
                referer = data,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsed<ResponseHash>()
            return Jsoup.parse(res.embed_url).selectFirst("iframe")?.attr("src") ?: ""
        }

        if (data.startsWith("{")) {
            val loadData = tryParseJson<LinkData>(data) ?: return false
            val source = fetchSource(loadData.post.orEmpty(), loadData.nume.orEmpty(), loadData.type.orEmpty())
            if (!source.contains("youtube")) loadExtractor(source, "$mainUrl/", subtitleCallback, callback)
        } else {
            val document = app.get(data).document
            val id = document.selectFirst("ul#playeroptionsul > li")?.attr("data-post") ?: return false
            val type = if (data.contains("/movies/")) "movie" else "tv"

            document.select("ul#playeroptionsul > li").amap { li ->
                val source = fetchSource(id, li.attr("data-nume"), type)
                if (!source.contains("youtube")) {
                    val realSource = if (source.contains("ok.ru")) "https:$source" else source
                    loadExtractor(realSource, "$mainUrl/", subtitleCallback, callback)
                }
            }
        }

        if (data.contains("hdmovie2")) {
            val directLinks = app.get(data).document.selectFirst("p > a")?.attr("href")
            directLinks?.let { l ->
                app.get(l).document.select("p > a").forEach { element ->
                    val label = element.selectFirst("button")?.text() ?: ""
                    if (label.contains("GDFlix", true)) {
                        val redirected = app.get(element.attr("href"), allowRedirects = false).headers["location"] ?: ""
                        loadExtractor(redirected, name, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }

    data class LinkData(val type: String? = null, val post: String? = null, val nume: String? = null)
    data class ResponseHash(@JsonProperty("embed_url") val embed_url: String)
}
