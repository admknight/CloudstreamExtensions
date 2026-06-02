package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup
import java.util.*

class KawaiifuProvider : MainAPI() {
    override var mainUrl = "https://kawaiifu.com"
    override var name = "Kawaiifu"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val resp = app.get(mainUrl).text
        val soup = Jsoup.parse(resp)

        items.add(HomePageList("Latest Updates", soup.select(".today-update .item").mapNotNull {
            val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val url = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            val isDub = title.contains("(DUB)")
            newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                addDubStatus(isDub, !isDub)
            }
        }))
        
        for (section in soup.select(".section")) {
            try {
                val title = section.selectFirst(".title")?.text() ?: continue
                val anime = section.select(".list-film > .item").mapNotNull { ani ->
                    val animTitle = ani.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
                    val url = ani.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val poster = ani.selectFirst("img")?.attr("src")
                    val isDub = animTitle.contains("(DUB)")
                    newAnimeSearchResponse(animTitle, url, TvType.Anime) {
                        this.posterUrl = poster
                        addDubStatus(isDub, !isDub)
                    }
                }
                items.add(HomePageList(title, anime))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search-movie?keyword=${query}"
        val html = app.get(link).text
        val soup = Jsoup.parse(html)

        return soup.select(".item").mapNotNull {
            val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            val uri = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val isDub = title.contains("(DUB)")
            newAnimeSearchResponse(title, uri, TvType.Anime) {
                this.posterUrl = poster
                addDubStatus(isDub, !isDub)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        val title = soup.selectFirst(".title")?.text() ?: ""
        val tags = soup.select(".table a[href*=\"/tag/\"]").map { tag -> tag.text() }
        val description = soup.select(".sub-desc p")
            .filter { it -> it.select("strong").isEmpty() && it.select("iframe").isEmpty() }
            .joinToString("\n") { it.text() }
        val year = url.split("/").find { it.contains("-") }?.split("-")?.getOrNull(1)?.toIntOrNull()

        val episodesLink = soup.selectFirst("a[href*=\".html-episode\"]")?.attr("href")
            ?: throw ErrorLoadingException("Error getting episode list")
        val episodes = Jsoup.parse(
            app.get(episodesLink).text
        ).selectFirst(".list-ep")?.select("li")?.map {
            val epName = it.text().trim()
            newEpisode(it.selectFirst("a")!!.attr("href")) {
                this.name = if (epName.toIntOrNull() != null) "Episode $epName" else epName
            }
        } ?: emptyList()
        
        val poster = soup.selectFirst("a.thumb > img")?.attr("src")

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.year = year
            this.posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val htmlSource = app.get(data).text
        val soup = Jsoup.parse(htmlSource)

        val episodeNum = data.substringAfter("ep=", "").substringBefore("&").toIntOrNull()

        val servers = soup.select(".list-server").mapNotNull {
            val serverName = it.selectFirst(".server-name")?.text() ?: return@mapNotNull null
            val episodes = it.select(".list-ep > li > a")
                .map { episode -> Pair(episode.attr("href"), episode.text()) }
            
            val episode = if (episodeNum == null) episodes.getOrNull(0) else episodes.find { ep ->
                ep.first.substringAfter("ep=", "").substringBefore("&").toIntOrNull() == episodeNum
            }
            if (episode == null) return@mapNotNull null
            Pair(serverName, episode)
        }.map { (serverName, episode) ->
            if (episode.first == data) {
                val sources = soup.select("video > source")
                    .map { source -> Pair(source.attr("src"), source.attr("data-quality")) }
                Triple(serverName, sources, episode.second)
            } else {
                val html = app.get(episode.first).text
                val s = Jsoup.parse(html)
                val sources = s.select("video > source")
                    .map { source -> Pair(source.attr("src"), source.attr("data-quality")) }
                Triple(serverName, sources, episode.second)
            }
        }

        servers.forEach { (serverName, sources, _) ->
            sources.forEach { (sourceUrl, quality) ->
                callback(
                    newExtractorLink(
                        "Kawaiifu",
                        serverName,
                        sourceUrl,
                        INFER_TYPE
                    ) {
                        this.quality = getQualityFromName(quality)
                    }
                )
            }
        }
        return true
    }
}
