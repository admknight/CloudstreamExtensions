package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.Jsoup

class AllAnimeProvider : MainAPI() {
    override var mainUrl = "https://allanime.site"
    override var name = "AllAnime"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/api?variables=%7B%22type%22%3A%22anime%22%2C%22size%22%3A30%2C%22dateRange%22%3A7%2C%22page%22%3A1%7D&extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%22378f870e28f731206f40b28489f38f158be5b7468ad921a8d01f8480cf890886%22%7D%22%2C%22limit%22%3A26%2C%22page%22%3A1%2C%22translationType%22%3A%22sub%22%2C%22countryOrigin%22%3A%22ALL%22%7D&extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%229497e289873d2a71d808c109786a51d9515598687771743f01c480cf890886%22%7D%7D", "Popular Anime"),
            Pair("$mainUrl/api?variables=%7B%22search%22%3A%7B%22allowAdult%22%3Afalse%2C%22allowUnknown%22%3Afalse%2C%22isManga%22%3Afalse%7D%2C%22limit%22%3A26%2C%22page%22%3A1%2C%22translationType%22%3A%22sub%22%2C%22countryOrigin%22%3A%22ALL%22%7D&extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%229497e289873d2a71d808c109786a51d9515598687771743f01c480cf890886%22%7D%7D", "Recently Updated"),
        )

        val items = ArrayList<HomePageList>()
        for (i in urls) {
            try {
                val response = app.get(i.first).text
                val results = parseJson<AllAnimeQuery>(response).data.shows.edges.map {
                    newAnimeSearchResponse(it.name, "$mainUrl/anime/${it.Id}", TvType.Anime) {
                        this.posterUrl = it.thumbnail
                        addDubStatus(it.availableEpisodes.dub != 0, it.availableEpisodes.sub != 0)
                    }
                }
                items.add(HomePageList(i.second, results))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    data class Edges(
        @JsonProperty("_id") val Id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("thumbnail") val thumbnail: String?,
        @JsonProperty("availableEpisodes") val availableEpisodes: AvailableEpisodes,
    )

    data class AvailableEpisodes(
        @JsonProperty("sub") val sub: Int,
        @JsonProperty("dub") val dub: Int,
        @JsonProperty("raw") val raw: Int,
    )

    data class Shows(
        @JsonProperty("edges") val edges: List<Edges>,
    )

    data class Data(
        @JsonProperty("shows") val shows: Shows,
    )

    data class AllAnimeQuery(
        @JsonProperty("data") val data: Data,
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url =
            "$mainUrl/api?variables=%7B%22search%22%3A%7B%22allowAdult%22%3Afalse%2C%22allowUnknown%22%3Afalse%2C%22isManga%22%3Afalse%2C%22query%22%3A%22$query%22%7D%2C%22limit%22%3A26%2C%22page%22%3A1%2C%22translationType%22%3A%22sub%22%2C%22countryOrigin%22%3A%22ALL%22%7D&extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%229497e289873d2a71d808c109786a51d9515598687771743f01c480cf890886%22%7D%7D"
        val response = app.get(url).text
        return parseJson<AllAnimeQuery>(response).data.shows.edges.map {
            newAnimeSearchResponse(it.name, "$mainUrl/anime/${it.Id}", TvType.Anime) {
                this.posterUrl = it.thumbnail
                addDubStatus(it.availableEpisodes.dub != 0, it.availableEpisodes.sub != 0)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val soup = Jsoup.parse(response)

        val id = url.substringAfterLast("/")
        val queryUrl =
            "$mainUrl/api?variables=%7B%22_id%22%3A%22$id%22%7D&extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%2292376993efb8e217088b90760f38b4c0926d24a06d0408542c67676711961601%22%7D%7D"
        val queryResponse = app.get(queryUrl).text

        data class DetailShow(@JsonProperty("show") val show: Edges)
        data class DetailData(@JsonProperty("data") val data: DetailShow)

        val showData = parseJson<DetailData>(queryResponse).data.show

        val title = showData.name
        val description = soup.selectFirst(".synopsis > .shorting > .content")?.text()
        val poster = showData.thumbnail

        val episodes = showData.availableEpisodes.let {
            Pair(if (it.sub != 0) ((1..it.sub).map { epNum ->
                newEpisode("$mainUrl/anime/${showData.Id}/episodes/sub/$epNum") {
                    this.episode = epNum
                }
            }) else null, if (it.dub != 0) ((1..it.dub).map { epNum ->
                newEpisode("$mainUrl/anime/${showData.Id}/episodes/dub/$epNum") {
                    this.episode = epNum
                }
            }) else null)
        }

        val characters = soup.select("div.character > div.card-character-box").mapNotNull {
            val img = it?.selectFirst("img")?.attr("src") ?: return@mapNotNull null
            val name = it.selectFirst("div > a")?.ownText() ?: return@mapNotNull null
            val role = when (it.selectFirst("div > .text-secondary")?.text()?.trim()) {
                "Main" -> ActorRole.Main
                "Supporting" -> ActorRole.Supporting
                else -> ActorRole.Background
            }
            Pair(Actor(name, img), role)
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            this.posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes.first ?: ArrayList())
            addEpisodes(DubStatus.Dubbed, episodes.second ?: ArrayList())
            addActors(characters)
            plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data).text
        val soup = Jsoup.parse(response)
        val script = soup.select("script").find { it.html().contains("var source =") }?.html()
        val streamUrl =
            script?.substringAfter("var source = \"")?.substringBefore("\"") ?: return false

        if (!loadExtractor(streamUrl, mainUrl, subtitleCallback, callback)) {
            callback(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = streamUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        return true
    }
}
