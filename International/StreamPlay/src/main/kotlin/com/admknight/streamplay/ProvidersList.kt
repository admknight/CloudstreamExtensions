package com.admknight.streamplay

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.admknight.streamplay.StreamPlayExtractor.invoke2embed
import com.admknight.streamplay.StreamPlayExtractor.invoke4khdhub
import com.admknight.streamplay.StreamPlayExtractor.invokeAllMovieland
import com.admknight.streamplay.StreamPlayExtractor.invokeAnichi
import com.admknight.streamplay.StreamPlayExtractor.invokeAnimepahe
import com.admknight.streamplay.StreamPlayExtractor.invokeAnimetosho
import com.admknight.streamplay.StreamPlayExtractor.invokeAnimex
import com.admknight.streamplay.StreamPlayExtractor.invokeAnizone
import com.admknight.streamplay.StreamPlayExtractor.invokeBollyflix
import com.admknight.streamplay.StreamPlayExtractor.invokeCineVood
import com.admknight.streamplay.StreamPlayExtractor.invokeDahmerMovies
import com.admknight.streamplay.StreamPlayExtractor.invokeDooflix
import com.admknight.streamplay.StreamPlayExtractor.invokeDudefilms
import com.admknight.streamplay.StreamPlayExtractor.invokeFilmyfiy
import com.admknight.streamplay.StreamPlayExtractor.invokeHdmovie2
import com.admknight.streamplay.StreamPlayExtractor.invokeHexa
import com.admknight.streamplay.StreamPlayExtractor.invokeHianime
import com.admknight.streamplay.StreamPlayExtractor.invokeHindmoviez
import com.admknight.streamplay.StreamPlayExtractor.invokeKickAssAnime
import com.admknight.streamplay.StreamPlayExtractor.invokeKisskh
import com.admknight.streamplay.StreamPlayExtractor.invokeM4uhd
import com.admknight.streamplay.StreamPlayExtractor.invokeMapple
import com.admknight.streamplay.StreamPlayExtractor.invokeMovieBox
import com.admknight.streamplay.StreamPlayExtractor.invokeMovies4u
import com.admknight.streamplay.StreamPlayExtractor.invokeMoviesApi
import com.admknight.streamplay.StreamPlayExtractor.invokeMoviesdrive
import com.admknight.streamplay.StreamPlayExtractor.invokeMoviesmod
import com.admknight.streamplay.StreamPlayExtractor.invokeMultimovies
import com.admknight.streamplay.StreamPlayExtractor.invokeNepu
import com.admknight.streamplay.StreamPlayExtractor.invokeNinetv
import com.admknight.streamplay.StreamPlayExtractor.invokePeachify
import com.admknight.streamplay.StreamPlayExtractor.invokeReAnime
import com.admknight.streamplay.StreamPlayExtractor.invokeRiveStream
import com.admknight.streamplay.StreamPlayExtractor.invokeRogmovies
import com.admknight.streamplay.StreamPlayExtractor.invokeSubtitleAPI
import com.admknight.streamplay.StreamPlayExtractor.invokeSuperstream
import com.admknight.streamplay.StreamPlayExtractor.invokeTokyoInsider
import com.admknight.streamplay.StreamPlayExtractor.invokeTopMovies
import com.admknight.streamplay.StreamPlayExtractor.invokeUhdmovies
import com.admknight.streamplay.StreamPlayExtractor.invokeVegamovies
import com.admknight.streamplay.StreamPlayExtractor.invokeVidFast
import com.admknight.streamplay.StreamPlayExtractor.invokeVidSrcXyz
import com.admknight.streamplay.StreamPlayExtractor.invokeVideasy
import com.admknight.streamplay.StreamPlayExtractor.invokeVidlink
import com.admknight.streamplay.StreamPlayExtractor.invokeVidzee
import com.admknight.streamplay.StreamPlayExtractor.invokeWatchsomuch
import com.admknight.streamplay.StreamPlayExtractor.invokeWYZIESubs
import com.admknight.streamplay.StreamPlayExtractor.invokeXpass
import com.admknight.streamplay.StreamPlayExtractor.invokeZinkmovies
import com.admknight.streamplay.StreamPlayExtractor.invokeZshow
import com.admknight.streamplay.StreamPlayExtractor.invokecinemacity
import com.admknight.streamplay.StreamPlayExtractor.invokehdhub4u
import com.admknight.streamplay.StreamPlayExtractor.invokevaplayer
import com.admknight.streamplay.StreamPlayExtractor.invokevidrock
import com.admknight.streamplay.StreamPlayExtractor.resolveAnimeIds

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: StreamPlay.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        token: String,
        dahmerMoviesAPI: String
    ) -> Unit
)

private fun getDubStatus(res: StreamPlay.LinkData): String {
    return when {
        res.isMovie == true -> "Movie"
        res.isDub -> "DUB"
        else -> "SUB"
    }
}

private suspend fun getAnimeIds(res: StreamPlay.LinkData): StreamPlayExtractor.AnimeResolvedIds {
    val cacheKey = "${res.title}_${res.date ?: res.airedDate}_${res.season ?: 0}"

    val cached = StreamPlayCache.getCachedAnimeIds(cacheKey)
    if (cached != null) {
        return StreamPlayExtractor.AnimeResolvedIds(
            malId = cached.malId?.toIntOrNull(),
            anilistId = cached.anilistId?.toIntOrNull(),
            anidbEid = 0,
            zoroIds = cached.zoroId?.split(",")?.filter { it.isNotBlank() },
            zoroTitle = null,
            aniXL = null,
            kaasSlug = null,
            animepaheUrl = null,
            animekaiId = cached.animekaiId,
            tmdbYear = null
        )
    }

    val ids = resolveAnimeIds(res.title, res.date, res.airedDate, res.season, res.episode)

    StreamPlayCache.cacheAnimeIds(
        cacheKey,
        StreamPlayCache.AnimeIdMapping(
            anilistId = null,
            malId = ids.malId?.toString(),
            kitsuId = null,
            zoroId = ids.zoroIds?.joinToString(",")
        )
    )

    return ids
}

private val providers by lazy {
    listOf(
        Provider("uhdmovies", "UHD Movies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeUhdmovies(res.title, res.year, res.season, res.episode, callback, subtitleCallback)
        },
        Provider("hianime", "HiAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.malId?.let { invokeHianime(it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("animetosho", "AnimeTosho") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.malId?.let { invokeAnimetosho(it, res.episode, subtitleCallback, callback, getDubStatus(res), ids.anidbEid) }
            }
        },
        Provider("ReAnime", "ReAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.anilistId?.let { invokeReAnime(it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("Animex", "Animex") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                invokeAnimex(ids.malId, ids.anilistId, res.jpTitle, res.episode, subtitleCallback, callback, getDubStatus(res))
            }
        },
        Provider("kickass", "KickAssAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.kaasSlug?.let { invokeKickAssAnime(res.title, it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("animepahe", "AnimePahe") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.animepaheUrl?.let { invokeAnimepahe(it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("anichi", "Anichi / AllAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                invokeAnichi(res.jpTitle, res.title, ids.tmdbYear, res.episode, subtitleCallback, callback, getDubStatus(res))
            }
        },
        Provider("tokyoinsider", "Tokyo Insider") { res, _, callback, _, _ ->
            if (res.isAnime) {
                invokeTokyoInsider(res.jpTitle, res.title, res.episode, callback, getDubStatus(res))
            }
        },
        Provider("anizone", "AniZone") { res, subtitleCallback, callback, _, _ ->
            Log.d("Phisher",res.jpTitle.toString())
            if (res.isAnime) {
                invokeAnizone(res.jpTitle, res.episode, subtitleCallback , callback, getDubStatus(res))
            }
        },
        Provider("topmovies", "Top Movies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeTopMovies(res.imdbId,
                res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviesmod", "MoviesMod") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeMoviesmod(res.title,res.imdbId,
                res.season, res.episode, subtitleCallback, callback)
        },
        Provider("bollyflix", "Bollyflix") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeBollyflix(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("watchsomuch", "WatchSoMuch") { res, subtitleCallback, _, _, _ ->
            if (!res.isAnime) invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("ninetv", "NineTV") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeNinetv(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("allmovieland", "AllMovieland") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeAllMovieland(res.imdbId, res.season, res.episode, callback)
        },
        Provider("vegamovies", "VegaMovies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isBollywood) invokeVegamovies(res.title, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("Rogmovies", "RogMovies") { res, subtitleCallback, callback, _, _ ->
            if (res.isBollywood) invokeRogmovies(res.title, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("multimovies", "MultiMovies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("zshow", "ZShow") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeZshow(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("nepu", "Nepu") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeNepu(res.title, res.airedYear ?: res.year, res.season, res.episode, callback)
        },
        Provider("moviesdrive", "MoviesDrive") { res, subtitleCallback, callback, _, _ ->
            invokeMoviesdrive(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("superstream", "SuperStream") { res, _, callback, token, _ ->
            val status = getDubStatus(res)
            val isAnime = res.isAnime
            if (isAnime && status != "SUB") return@Provider

            if (res.imdbId != null && token.isNotEmpty()) {
                invokeSuperstream(
                    token,
                    res.imdbId,
                    res.id,
                    res.season,
                    res.episode,
                    callback
                )
            }
        },
        Provider("vidsrcxyz", "VidSrcXyz") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback)
        },
        Provider("vidzeeapi", "Vidzee API") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeVidzee(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("4khdhub", "4kHdhub (Multi)") { res, subtitleCallback, callback, _, _ ->
            invoke4khdhub(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdhub4u", "Hdhub4u (Multi)") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokehdhub4u(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdmovie2", "Hdmovie2") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeHdmovie2(res.title, res.year,
                res.episode, subtitleCallback, callback)
        },
        Provider("rivestream", "RiveStream") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeRiveStream(res.id, res.season, res.episode, callback)
        },
        Provider("moviebox", "MovieBox (Multi)") { res, subtitleCallback, callback, _, _ ->
            invokeMovieBox(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidrock", "Vidrock") { res, _, callback, _, _ ->
            if (!res.isAnime) invokevidrock(res.id, res.season, res.episode, callback)
        },
        Provider("vidlink", "Vidlink") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeVidlink(res.id, res.season, res.episode, callback)
        },
        Provider("kisskh", "KissKH (Asian Drama)") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeKisskh(res.title, res.season, res.episode, res.lastSeason, subtitleCallback, callback)
        },
        Provider("dahmermovies", "DahmerMovies") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeDahmerMovies(res.title, res.year, res.season, res.episode, callback)
        },
        Provider("vidfast", "VidFast") { res, _, callback, _, _ ->
            invokeVidFast(res.id, res.season,res.episode, callback)
        },
        Provider("VidEasy", "VidEasy") { res, subtitleCallback, callback, _, _ ->
            invokeVideasy(res.title,res.id, res.imdbId, res.year, res.season,res.episode, subtitleCallback, callback )
        },
        Provider("moviesapi", "MoviesApi Club") { res, _, callback, _, _ ->
            invokeMoviesApi(res.id, res.season, res.episode, callback)
        },
        Provider("CinemaCity", "CinemaCity") { res, _, callback, _, _ ->
            invokecinemacity(res.imdbId, res.season,res.episode,  callback)
        },
        Provider("HexaSU", "HexaSU") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeHexa(res.id, res.season, res.episode, callback)
        },
        Provider("Hindmoviez", "HindMoviez") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeHindmoviez(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("Movies4u", "Movies4u") { res, subtitleCallback, callback, _, _ ->
            invokeMovies4u(res.imdbId, res.title,res.year, res.season, res.episode, subtitleCallback ,callback)
        },
        Provider("M4uhd", "M4uhd") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeM4uhd(res.title,
                res.season, res.episode, subtitleCallback ,callback)
        },
        Provider("MappleTV", "MappleTV") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeMapple(res.id, res.season, res.episode ,callback)
        },
        Provider("WyZIESUB", "WyZIESUB (Subtitles)") { res, subtitleCallback, _, _, _ ->
            invokeWYZIESubs(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("SubtitleAPI", "SubtitleAPI (Subtitles)") { res, subtitleCallback, _, _, _ ->
            invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("CineVood", "CineVood (Movies Only)") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeCineVood(res.imdbId, subtitleCallback, callback)
        },
        Provider("Filmyfiy", "Filmyfiy (Movies Only)") { res, sub, cb, _, _ ->
            if (!res.isAnime && res.season == null) invokeFilmyfiy(res.title, sub, cb)
        },
        Provider("2Embed", "2Embed") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invoke2embed(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("DooFlix", "DooFlix") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeDooflix(res.id, res.season, res.episode, callback)
        },
        Provider("Xpass", "Xpass") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeXpass(res.id, res.season, res.episode, callback, )
        },
        Provider("vaplayer", "Vaplayer") { res, subtitleCallback, callback, _, _ ->
            invokevaplayer(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("Dudefilms", "Dudefilms") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeDudefilms(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("Zinkmovies", "Zinkmovies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeZinkmovies(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("Peachify", "Peachify") { res, _, callback, _, _ ->
            if (!res.isAnime) invokePeachify(res.id, res.season, res.episode, callback)
        },
    )
}

fun buildProviders(): List<Provider> = providers



