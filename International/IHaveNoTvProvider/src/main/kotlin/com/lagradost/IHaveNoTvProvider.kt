package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLEncoder

class IHaveNoTvProvider : MainAPI() {
    override var mainUrl = "https://ihavenotv.com"
    override var name = "I Have No TV"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Documentary)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val allCategories = listOf(
            "astronomy", "brain", "creativity", "design", "economics", "environment",
            "health", "history", "lifehack", "math", "music", "nature", "people",
            "physics", "science", "technology", "travel"
        )

        val categories = allCategories.shuffled().take(3)
        val items = ArrayList<HomePageList>()

        categories.forEach { cat ->
            val soup = app.get("$mainUrl/category/$cat").document
            val results = soup.select(".episodesDiv .episode").mapNotNull { res ->
                val poster = res.selectFirst("img")?.attr("src")
                val aTag = if (res.html().contains("/series/")) res.selectFirst(".episodeMeta > a") else res.selectFirst("a[href][title]")
                val title = aTag?.attr("title") ?: ""
                val href = fixUrl(aTag?.attr("href") ?: "")
                val year = Regex("""•?\s+(\d{4})\s+•""").find(res.selectFirst(".episodeMeta")?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()

                newMovieSearchResponse(title, href, TvType.Documentary) {
                    this.posterUrl = poster
                    this.year = year
                }
            }.take(5)
            
            if (results.isNotEmpty()) items.add(HomePageList(cat.replaceFirstChar { it.uppercase() }, results))
        }

        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${URLEncoder.encode(query, "UTF-8")}"
        val soup = app.get(url).document

        return soup.select(".episodesDiv .episode").mapNotNull { res ->
            val poster = res.selectFirst("img")?.attr("src")
            val aTag = if (res.html().contains("/series/")) res.selectFirst(".episodeMeta > a") else res.selectFirst("a[href][title]")
            val title = aTag?.attr("title") ?: ""
            val href = fixUrl(aTag?.attr("href") ?: "")
            val year = Regex("""•?\s+(\d{4})\s+•""").find(res.selectFirst(".episodeMeta")?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()

            newMovieSearchResponse(title, href, TvType.Documentary) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val isSeries = url.contains("/series/")
        val soup = app.get(url).document

        val container = soup.selectFirst(".container-fluid h1")?.parent()
        val title = if (isSeries) {
            container?.selectFirst("h1")?.text()?.split("•")?.firstOrNull()?.trim() ?: ""
        } else soup.selectFirst(".videoDetails strong")?.text() ?: ""
        
        val description = if (isSeries) container?.selectFirst("p")?.text() else soup.selectFirst(".videoDetails > p")?.text()

        var year: Int? = null
        val categories = mutableSetOf<String>()

        if (isSeries) {
            val episodes = container?.select(".episode")?.mapNotNull { ep ->
                val thumb = ep.selectFirst("img")?.attr("src")
                val aTag = ep.selectFirst("a[title]")
                val epLink = fixUrl(aTag?.attr("href") ?: return@mapNotNull null)
                val epMeta = ep.selectFirst(".episodeMeta")?.text() ?: ""
                
                val seasonPair = if (ep.selectFirst(".episodeMeta > strong")?.html()?.contains("S") == true) {
                    val split = ep.selectFirst(".episodeMeta > strong")?.text()?.split("E")
                    Pair(split?.firstOrNull()?.replace("S", "")?.toIntOrNull(), split?.getOrNull(1)?.toIntOrNull())
                } else Pair(null, null)

                year = year ?: Regex("""•?\s+(\d{4})\s+•""").find(epMeta)?.groupValues?.get(1)?.toIntOrNull()
                categories.addAll(ep.select(".episodeMeta > a[href*=\"/category/\"]").map { it.text().trim() })

                newEpisode(epLink) {
                    this.name = aTag.attr("title")
                    this.season = seasonPair.first
                    this.episode = seasonPair.second
                    this.posterUrl = thumb
                    this.description = ep.selectFirst(".episodeSynopsis")?.text()
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = episodes.firstOrNull()?.posterUrl
                this.year = year
                this.plot = description
                this.tags = categories.toList()
            }
        } else {
            val vDetails = soup.selectFirst(".videoDetails")
            val vYear = Regex("""•?\s+(\d{4})\s+•""").find(vDetails?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()
            val vTags = vDetails?.select("a[href*=\"/category/\"]")?.map { it.text().trim() }
            val vPoster = soup.selectFirst("[rel=\"image_src\"]")?.attr("href")

            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = vPoster
                this.year = vYear
                this.plot = description
                this.tags = vTags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val iframe = app.get(data).document.selectFirst("#videoWrap iframe")
        if (iframe != null) {
            loadExtractor(iframe.attr("src"), data, subtitleCallback, callback)
        }
        return true
    }
}
