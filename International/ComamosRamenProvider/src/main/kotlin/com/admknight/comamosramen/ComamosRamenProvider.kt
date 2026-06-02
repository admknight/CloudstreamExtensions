package com.admknight.comamosramen

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*

class ComamosRamenProvider : MainAPI() {
    override var mainUrl = "https://m.comamosramen.com"
    override var name = "ComamosRamen"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    data class HomeMain (@JsonProperty("props") var props : HomeProps? = HomeProps())
    data class HomeProps (@JsonProperty("pageProps") var pageProps : HomePageProps? = HomePageProps())
    data class HomePageProps (@JsonProperty("data") var data : HomeData? = HomeData())
    data class HomeData (@JsonProperty("sections") var sections : List<HomeSections> = listOf())
    data class HomeSections (@JsonProperty("data") var data : List<HomeDatum> = listOf(), @JsonProperty("name") var name : String? = null)
    data class HomeDatum (
        @JsonProperty("_id") var Id : String,
        @JsonProperty("status") var status : Status? = Status(),
        @JsonProperty("title") var title : String,
        @JsonProperty("img") var img : Img = Img(),
        @JsonProperty("lastEpisodeEdited") var lastEpisodeEdited : String? = null
    )
    data class Status (@JsonProperty("isOnAir") var isOnAir : Boolean? = null)
    data class Img (@JsonProperty("vertical") var vertical : String? = null)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val doc = app.get("https://comamosramen.com").document
        doc.select("script[type=application/json]").forEach { script ->
            if (script.data().contains("pageProps")) {
                val json = try { parseJson<HomeMain>(script.data()) } catch (_: Exception) { null }
                json?.props?.pageProps?.data?.sections?.forEach { section ->
                    val results = section.data.map { data ->
                        val link = "$mainUrl/v/${data.Id}/${data.title.replace(" ", "-")}"
                        val img = "https://img.comamosramen.com/${data.img.vertical}-high.jpg"
                        val epNum = Regex("(\\d+)$").find(data.lastEpisodeEdited ?: "")?.value?.toIntOrNull()
                        val dubStat = if (data.title.contains("Latino")) DubStatus.Dubbed else DubStatus.Subbed
                        
                        newAnimeSearchResponse(data.title, fixUrl(link), TvType.Anime) {
                            this.posterUrl = img
                            addDubStatus(dubStat, epNum)
                        }
                    }
                    if (results.isNotEmpty()) items.add(HomePageList(section.name ?: "Unknown", results))
                }
            }
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    data class SearchOb (@JsonProperty("props") var props : SearchProps? = SearchProps())
    data class SearchProps (@JsonProperty("pageProps") var pageProps : SearchPageProps? = SearchPageProps())
    data class SearchPageProps (@JsonProperty("data") var data : DataSS? = DataSS())
    data class DataSS (@JsonProperty("data") var datum : ArrayList<DatumSearch> = arrayListOf())
    data class DatumSearch (@JsonProperty("_id") var Id : String? = null, @JsonProperty("img") var img : Img? = Img(), @JsonProperty("title") var title : String)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "${mainUrl.replace("m.", "")}/buscar/${query}"
        val script = app.get(url).document.selectFirst("script[type=application/json]")?.data() ?: ""
        val json = try { parseJson<SearchOb>(script) } catch (_: Exception) { null }
        
        return json?.props?.pageProps?.data?.datum?.map {
            val img = "https://img.comamosramen.com/${it.img?.vertical}-high.jpg"
            val link = "$mainUrl/v/${it.Id}/${it.title.replace(" ", "-")}"
            newAnimeSearchResponse(it.title, link, TvType.AsianDrama) {
                this.posterUrl = img
                addDubStatus(if (it.title.contains("Latino")) DubStatus.Dubbed else DubStatus.Subbed)
            }
        } ?: emptyList()
    }

    data class LoadMain (@JsonProperty("props") var props : LoadProps? = LoadProps())
    data class LoadProps (@JsonProperty("pageProps") var pageProps : LoadPageProps? = LoadPageProps())
    data class LoadPageProps (@JsonProperty("data") var data : LoadData? = LoadData())
    data class LoadData (
        @JsonProperty("_id") var Id : String? = null,
        @JsonProperty("metadata") var metadata : LoadMetadata? = LoadMetadata(),
        @JsonProperty("status") var status : Status? = Status(),
        @JsonProperty("title") var title : String? = null,
        @JsonProperty("description") var description : String? = null,
        @JsonProperty("img") var img : Img? = Img(),
        @JsonProperty("seasons") var seasons : ArrayList<Seasons> = arrayListOf()
    )
    data class LoadMetadata (@JsonProperty("year") var year : Int? = null, @JsonProperty("tags") var tags : ArrayList<String> = arrayListOf())
    data class Seasons (@JsonProperty("season") var season : Int? = null, @JsonProperty("episodes") var episodes : ArrayList<Episodes> = arrayListOf())
    data class Episodes (@JsonProperty("episode") var episode : Int? = null, @JsonProperty("players") var players : ArrayList<Players> = arrayListOf())
    data class Players (@JsonProperty("id") var id : String? = null, @JsonProperty("name") var name : String? = null)

    override suspend fun load(url: String): LoadResponse {
        val scriptDoc = app.get(url).document.selectFirst("script[type=application/json]")?.data() ?: ""
        val json = parseJson<LoadMain>(scriptDoc)
        val data = json.props?.pageProps?.data ?: throw ErrorLoadingException("Invalid data")
        
        val title = data.title ?: ""
        val plotStr = data.description?.substringAfter("Sinopsis")?.trim()
        val img = "https://img.comamosramen.com/${data.img?.vertical}-high.jpg"
        val status = if (data.status?.isOnAir == true) ShowStatus.Ongoing else ShowStatus.Completed
        
        val episodes = ArrayList<Episode>()
        data.seasons.forEach { s ->
            s.episodes.forEach { e ->
                episodes.add(newEpisode("$mainUrl/v/${data.Id}/${title.replace(" ", "-")}/${s.season}-${e.episode}") {
                    this.season = s.season
                    this.episode = e.episode
                })
            }
        }
        
        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = img
            this.year = data.metadata?.year
            this.plot = plotStr
            this.showStatus = status
            this.tags = data.metadata?.tags
        }
    }

    data class LoadLinksMain (@JsonProperty("SeasonID") var SeasonID : Int? = null, @JsonProperty("EpisodeID") var EpisodeID : Int? = null, @JsonProperty("Servers") var Servers : ArrayList<Players> = arrayListOf())

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val script = app.get(data).document.selectFirst("script[type=application/json]")?.data() ?: ""
        val json = parseJson<LoadMain>(script)
        val match = Regex("(\\d+)-(\\d+)$").find(data) ?: return false
        val sNum = match.groupValues[1].toIntOrNull()
        val eNum = match.groupValues[2].toIntOrNull()

        json.props?.pageProps?.data?.seasons?.forEach { s ->
            if (s.season == sNum) {
                s.episodes.forEach { e ->
                    if (e.episode == eNum) {
                        e.players.forEach { p ->
                            val base = when {
                                p.name?.contains("SB") == true -> "https://sbplay2.xyz/e/"
                                p.name?.contains("dood") == true -> "https://dood.la/e/"
                                p.name?.contains("Voe") == true -> "https://voe.sx/e/"
                                p.name?.contains("Fembed") == true -> "https://embedsito.com/v/"
                                p.name?.contains("Okru") == true -> "http://ok.ru/videoembed/"
                                else -> null
                            }
                            val pId = p.id?.replace(Regex("/v/|v/|/|\\.html"), "") ?: ""
                            if (base != null) loadExtractor(base + pId, data, subtitleCallback, callback)
                        }
                    }
                }
            }
        }
        return true
    }
}
