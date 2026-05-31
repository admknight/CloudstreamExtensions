package com.admknight.anichi

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AnichiUtils {

    suspend fun getTracker(
        name: String?,
        altName: String?,
        year: Int?,
        season: String?,
        type: String?
    ): AnilistAPIResponse.anilistMedia? {

        val primary = fetchId(name, year, season, type)
        if (primary?.id != null) return primary

        val secondary = fetchId(altName, year, season, type)
        if (secondary?.id != null) return secondary

        return null
    }

    suspend fun fetchId(title: String?, year: Int?, season: String?, type: String?): AnilistAPIResponse.anilistMedia? {

        if (title.isNullOrBlank()) return null

        val query = """
        query (${'$'}search: String, ${'$'}type: MediaType) {
          Page(perPage: 10) {
            media(search: ${'$'}search, type: ${'$'}type) {
              id
              idMal
              seasonYear
              format
              title { romaji english native }
              synonyms
              coverImage { extraLarge large }
              bannerImage
              averageScore
              status
              description
              episodes
              genres
              episodeDuration
              prevideos
              nextAiringEpisode { episode }
              airingSchedule { nodes { episode } }
            }
          }
        }
    """.trimIndent()

        val variables = mapOf(
            "search" to title,
            "type" to "ANIME"
        )

        val body = mapOf("query" to query, "variables" to variables)
            .toJson()
            .toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val results = try {
            app.post("https://graphql.anilist.co", requestBody = body)
                .parsedSafe<AnilistAPIResponse>()
                ?.data?.page?.media
        } catch (_: Throwable) {
            null
        } ?: return null

        return results.maxByOrNull { media ->

            var score = 0

            // Year match
            if (year != null && media.seasonYear == year) score += 3

            // Format match
            if (!type.isNullOrBlank() && media.format?.equals(type, true) == true) score += 2

            // Collect all titles safely
            val titles = buildList {
                media.title?.romaji?.let { add(it) }
                media.title?.english?.let { add(it) }
                media.title?.native?.let { add(it) }
                media.synonyms?.let { addAll(it) }
            }

            // Exact match
            if (titles.any { it.equals(title, ignoreCase = true) }) score += 5

            // Partial match
            if (titles.any { it.contains(title, ignoreCase = true) }) score += 2

            score
        }
    }

    suspend fun aniToMal(id: String): String? {
        return app.post(
                        "https://graphql.anilist.co",
                        data =
                                mapOf(
                                        "query" to "{Media(id:$id,type:ANIME){idMal}}",
                                )
                )
                .parsedSafe<AnilistDataAni>()
                ?.data
                ?.media
                ?.idMal
    }

    suspend fun getM3u8Qualities(
            m3u8Link: String,
            referer: String,
            qualityName: String,
    ): List<ExtractorLink> {
        return M3u8Helper.generateM3u8(
                qualityName,
                m3u8Link,
                referer
        )
    }

    fun String.getHost(): String {
        return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
    }

    fun String.fixUrlPath(apiEndPoint: String): String {
        return if (this.contains(".json?")) apiEndPoint + this
        else apiEndPoint + URI(this).path + ".json?" + URI(this).query
    }

    fun fixSourceUrls(url: String, source: String?): String? {
        return if (source == "Ak" || url.contains("/player/vitemb")) {
            com.lagradost.cloudstream3.utils.AppUtils.tryParseJson<AnichiParser.AkIframe>(base64Decode(url.substringAfter("=")))?.idUrl
        } else {
            url.replace(" ", "%20")
        }
    }

    suspend fun anilistAPICall(query: String): AnilistAPIResponse {
        val data = mapOf("query" to query)
        val headerJSON = mapOf("Accept" to "application/json", "Content-Type" to "application/json")
        return app.post("https://graphql.anilist.co", headers = headerJSON, data = data)
            .parsedSafe<AnilistAPIResponse>()
            ?: throw Exception("Unable to fetch or parse Anilist api response")
    }
}

data class AnilistDataAni(val data: AnilistDataAniInner)
data class AnilistDataAniInner(val media: AnilistMediaIdMal)
data class AnilistMediaIdMal(val idMal: String?)

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}

data class AnilistAPIResponse(
    @param:JsonProperty("data") val data: AnilistData,
) {
    data class AnilistData(
        @param:JsonProperty("Page") val page: AnilistPage? = null,
        @param:JsonProperty("Media") val media: anilistMedia? = null,
    )

    data class AnilistPage(
        @param:JsonProperty("media") val media: List<anilistMedia>? = null,
    )

    data class anilistMedia(
        @param:JsonProperty("id") val id: Int? = null,
        @param:JsonProperty("idMal") val idMal: Int? = null,
        @param:JsonProperty("seasonYear") val seasonYear: Int? = null,
        @param:JsonProperty("episodes") val episodes: Int? = null,
        @param:JsonProperty("title") val title: Title? = null,
        @param:JsonProperty("season") val season: String? = null,
        @param:JsonProperty("genres") val genres: List<String>? = null,
        @param:JsonProperty("averageScore") val averageScore: Int? = null,
        @param:JsonProperty("status") val status: String? = null,
        @param:JsonProperty("description") val description: String? = null,
        @param:JsonProperty("coverImage") val coverImage: CoverImage? = null,
        @param:JsonProperty("bannerImage") val bannerImage: String? = null,
        @param:JsonProperty("format") val format: String? = null,
        @param:JsonProperty("synonyms") val synonyms: List<String>? = null,
        @param:JsonProperty("startDate") val startDate: StartDate? = null,
    )

    data class Title(
        @param:JsonProperty("romaji") val romaji: String? = null,
        @param:JsonProperty("english") val english: String? = null,
        @param:JsonProperty("native") val native: String? = null,
    )

    data class CoverImage(
        @param:JsonProperty("extraLarge") val extraLarge: String? = null,
        @param:JsonProperty("large") val large: String? = null,
        @param:JsonProperty("medium") val medium: String? = null,
    )

    data class StartDate(
        @param:JsonProperty("year") val year: Int? = null,
    )
}

data class MetaAnimeData(
    val titles: Map<String, String>? = null,
    val images: List<MetaImageData>? = null,
    val episodes: Map<String, MetaEpisodeData>? = null,
    val mappings: MetaMappingsData? = null
)

data class MetaImageData(val coverType: String?, val url: String?)
data class MetaEpisodeData(
    val title: Map<String, String>?,
    val image: String?,
    val overview: String?,
    val rating: String?,
    val runtime: Int?,
    val airDateUtc: String?
)
data class MetaMappingsData(val themoviedb_id: String?)


suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
                    link.url,
                ) {
                    this.quality = when {
                        else -> quality ?: link.quality
                    }
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}
