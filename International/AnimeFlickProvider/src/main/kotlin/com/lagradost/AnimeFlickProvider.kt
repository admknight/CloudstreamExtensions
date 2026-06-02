package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup
import java.util.*

class AnimeFlickProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("OVA") || t.contains("Special") -> TvType.OVA
                t.contains("Movie") -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }
    }

    override var mainUrl = "https://animeflick.net"
    override var name = "AnimeFlick"
    override val hasQuickSearch = false
    override val hasMainPage = false

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "https://animeflick.net/search.php?search=$query"
        val html = app.get(link).text
        val doc = Jsoup.parse(html)

        return doc.select(".row.mt-2").mapNotNull {
            val href = mainUrl + it.selectFirst("a")?.attr("href")
            val title = it.selectFirst("h5 > a")?.text() ?: return@mapNotNull null
            val poster = mainUrl + it.selectFirst("img")?.attr("src")?.replace("70x110", "225x320")
            
            newAnimeSearchResponse(title, href, getType(title)) {
                this.posterUrl = poster
                addDubStatus(DubStatus.Subbed)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val poster = mainUrl + doc.selectFirst("img.rounded")?.attr("src")
        val title = doc.selectFirst("h2.title")?.text() ?: ""
        val year = Regex("(\\d{4})").find(doc.selectFirst(".trending-year")?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val description = doc.selectFirst("p")?.text()
        val genres = doc.select("a[href*=\"genre-\"]").map { it.text() }

        val episodes = doc.select("#collapseOne .block-space > .row > div:nth-child(2)").map {
            val name = it.selectFirst("a")?.text()
            val link = mainUrl + it.selectFirst("a")?.attr("href")
            newEpisode(link, name)
        }.reversed()

        return newAnimeLoadResponse(title, url, getType(title)) {
            this.posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            this.plot = description
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).text
        val links = Regex("(https://.*?\\.mp4)").findAll(html).map { it.value }.toList()
        
        for (link in links) {
            val extractor = extractorApis.find { link.startsWith(it.mainUrl) }
            if (extractor != null) {
                extractor.getSafeUrl(link, data, subtitleCallback, callback)
            } else {
                callback(
                    newExtractorLink(name, "$name - Auto", link, INFER_TYPE) {
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
        return true
    }
}
