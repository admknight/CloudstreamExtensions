package com.admknight.anichi

import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object AnichiParser {

    data class AnichiLoadData(
        val hash: String,
        val dubStatus: String,
        val episode: String,
        val idMal: Int? = null,
    )

    data class JikanData(
        @param:JsonProperty("title") val title: String? = null,
        @param:JsonProperty("title_english") val title_english: String? = null,
        @param:JsonProperty("title_japanese") val title_japanese: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("season") val season: String? = null,
        @param:JsonProperty("type") val type: String? = null,
    )

    data class JikanResponse(
        @param:JsonProperty("data") val data: JikanData? = null,
    )

    data class AnichiQuery(@param:JsonProperty("data") val data: QueryData? = null)

    data class QueryData(
        @param:JsonProperty("shows") val shows: Shows? = null,
        @param:JsonProperty("queryListForTag") val queryListForTag: Shows? = null,
        @param:JsonProperty("queryPopular") val queryPopular: Shows? = null,
    )

    data class Shows(
        @param:JsonProperty("edges") val edges: List<Edges>? = arrayListOf(),
        @param:JsonProperty("recommendations") val recommendations: List<EdgesCard>? = arrayListOf(),
    )

    data class EdgesCard(
        @param:JsonProperty("anyCard") val anyCard: Edges? = null,
    )

    data class Edges(
        @param:JsonProperty("_id") val Id: String?,
        @param:JsonProperty("name") val name: String?,
        @param:JsonProperty("englishName") val englishName: String?,
        @param:JsonProperty("nativeName") val nativeName: String?,
        @param:JsonProperty("thumbnail") val thumbnail: String?,
        @param:JsonProperty("type") val type: String?,
        @param:JsonProperty("season") val season: Season?,
        @param:JsonProperty("score") val score: Double?,
        @param:JsonProperty("airedStart") val airedStart: AiredStart?,
        @param:JsonProperty("availableEpisodes") val availableEpisodes: AvailableEpisodes?,
        @param:JsonProperty("availableEpisodesDetail")
        val availableEpisodesDetail: AvailableEpisodesDetail?,
        @param:JsonProperty("studios") val studios: List<String>?,
        @param:JsonProperty("genres") val genres: List<String>?,
        @param:JsonProperty("averageScore") val averageScore: Int?,
        @param:JsonProperty("characters") val characters: List<Characters>?,
        @param:JsonProperty("altNames") val altNames: List<String>?,
        @param:JsonProperty("description") val description: String?,
        @param:JsonProperty("status") val status: String?,
        @param:JsonProperty("banner") val banner: String?,
        @param:JsonProperty("episodeDuration") val episodeDuration: Int?,
        @param:JsonProperty("prevideos") val prevideos: List<String> = emptyList(),
    )

    data class Season(
        @param:JsonProperty("quarter") val quarter: String,
        @param:JsonProperty("year") val year: Int
    )

    data class AiredStart(
        @param:JsonProperty("year") val year: Int,
        @param:JsonProperty("month") val month: Int,
        @param:JsonProperty("date") val date: Int
    )

    data class AvailableEpisodes(
        @param:JsonProperty("sub") val sub: Int,
        @param:JsonProperty("dub") val dub: Int,
        @param:JsonProperty("raw") val raw: Int
    )

    data class AvailableEpisodesDetail(
        @param:JsonProperty("sub") val sub: List<String>,
        @param:JsonProperty("dub") val dub: List<String>,
        @param:JsonProperty("raw") val raw: List<String>
    )

    data class Characters(
        @param:JsonProperty("image") val image: CharacterImage?,
        @param:JsonProperty("role") val role: String?,
        @param:JsonProperty("name") val name: CharacterName?,
    )

    data class CharacterImage(
        @param:JsonProperty("large") val large: String?,
        @param:JsonProperty("medium") val medium: String?
    )

    data class CharacterName(
        @param:JsonProperty("full") val full: String?,
        @param:JsonProperty("native") val native: String?
    )

    data class Detail(@param:JsonProperty("data") val data: DetailShow)

    data class DetailShow(@param:JsonProperty("show") val show: Edges)

    data class APIResponse(
        @param:JsonProperty("status") val status: Int? = null,
        @param:JsonProperty("result") val result: String? = null,
        val html: Document = Jsoup.parse(result ?: "")
    )

    data class LinksQuery(@param:JsonProperty("data") val data: LinkData? = LinkData())

    data class LinkData(@param:JsonProperty("episode") val episode: Episode? = Episode())

    data class Episode(
        @param:JsonProperty("sourceUrls") val sourceUrls: ArrayList<SourceUrls> = arrayListOf(),
    )

    data class SourceUrls(
        @param:JsonProperty("sourceUrl") val sourceUrl: String? = null,
        @param:JsonProperty("downloads") val downloads: Downloads? = null,
        @param:JsonProperty("priority") val priority: Double? = null,
        @param:JsonProperty("sourceName") val sourceName: String? = null,
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("className") val className: String? = null,
        @param:JsonProperty("streamerId") val streamerId: String? = null
    )

    data class Downloads(
        @param:JsonProperty("sourceName") val sourceName: String? = null,
        @param:JsonProperty("downloadUrl") val downloadUrl: String? = null
    )

    data class AnichiVideoApiResponse(@param:JsonProperty("links") val links: List<Links>)

    data class Links(
        @param:JsonProperty("link") val link: String,
        @param:JsonProperty("hls") val hls: Boolean? = null,
        @param:JsonProperty("resolutionStr") val resolutionStr: String,
        @param:JsonProperty("src") val src: String? = null,
        @param:JsonProperty("headers") val Headers: Headers? = null,
        @param:JsonProperty("portData") val portData: PortData? = null,
        @param:JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = arrayListOf(),
    )

    data class Headers(
        @param:JsonProperty("Referer") val referer: String? = null,
        @param:JsonProperty("Origin") val origin: String? = null,
        @param:JsonProperty("user-agent") val userAgent: String? = null,
    )

    data class PortData(
        @param:JsonProperty("streams") val streams: ArrayList<Stream>? = arrayListOf(),
    )

    data class Stream(
        @param:JsonProperty("format") val format: String? = null,
        @param:JsonProperty("audio_lang") val audio_lang: String? = null,
        @param:JsonProperty("hardsub_lang") val hardsub_lang: String? = null,
        @param:JsonProperty("url") val url: String? = null,
    )

    data class Subtitles(
        @param:JsonProperty("lang") val lang: String?,
        @param:JsonProperty("label") val label: String?,
        @param:JsonProperty("src") val src: String?,
    )

    data class AkIframe(
        @param:JsonProperty("idUrl") val idUrl: String? = null,
    )

    data class AnichiDownload(
        @param:JsonProperty("links") val links: List<AnichiDownloadLink>,
    )

    data class AnichiDownloadLink(
        @param:JsonProperty("link") val link: String,
        @param:JsonProperty("hls") val hls: Boolean,
        @param:JsonProperty("mp4") val mp4: Boolean? = null,
        @param:JsonProperty("resolutionStr") val resolutionStr: String,
        @param:JsonProperty("priority") val priority: Long? = null,
        @param:JsonProperty("src") val src: String? = null,
    )
}
