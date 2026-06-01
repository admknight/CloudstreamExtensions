package com.lagradost.tvtwofourseven

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URI

class Tvtwofourseven : MainAPI() {
    override var mainUrl = "http://tv247.us"
    override var name = "Tv247"
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = listOf(
            Pair("$mainUrl/top-channels", "Top Channels"),
            Pair("$mainUrl/all-channels", "All Channels")
        ).map { (url,name) ->
            val homeItems =
                app.get(url).document.select("div.grid-items div.item").mapNotNull { item ->
                    item.toSearchResult()
                }
            HomePageList(name, homeItems)
        }.filter { it.list.isNotEmpty() }
        return newHomePageResponse(home)
    }

    private fun Element.toSearchResult(): LiveSearchResponse? {
        val title = this.selectFirst("div.layer-content a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        return newLiveSearchResponse(title, href, TvType.Live) {
            this.posterUrl = fixUrlNull(this@toSearchResult.select("img").attr("src"))
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/wp-admin/admin-ajax.php", data = mapOf(
                "action" to "ajaxsearchlite_search",
                "aslp" to query,
                "asid" to "1",
                "options" to "qtranslate_lang=0&set_intitle=None&set_incontent=None&set_inposts=None"
            ),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document.select("div.item").mapNotNull {
            val title = it.selectFirst("a")?.text() ?: return@mapNotNull null
            val href = fixUrl(it.selectFirst("a")!!.attr("href"))
            newLiveSearchResponse(title, href, TvType.Live) {
                this.posterUrl = fixUrlNull(
                    it.select("div.asl_image").attr("style").substringAfter("url(\"")
                        .substringBefore("\");")
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val data =
            document.select("script").find { it.data().contains("var channelName =") }?.data()
        val baseUrl = data?.substringAfter("baseUrl = \"")?.substringBefore("\";")
        val channel = data?.substringAfter("var channelName = \"")?.substringBefore("\";")
        val title = document.selectFirst("title")?.text()?.split("-")?.first()?.trim() ?: return null
        return newLiveStreamLoadResponse(title, url, "$baseUrl$channel.m3u8") {
            // DSL fields
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (URI(data).host == "cdn.espnfree.xyz") {
            M3u8Helper.generateM3u8(
                this.name,
                data,
                "$mainUrl/",
                headers = mapOf("Origin" to mainUrl, "X-Cache" to "HIT"),
            ).forEach(callback)
        } else {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = data,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.headers = mapOf("Origin" to mainUrl)
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        return true

    }
}
