package com.admknight.streamplay

import android.content.SharedPreferences
import com.lagradost.cloudstream3.LoadResponse

class StreamPlayAnime(sharedPref: SharedPreferences? = null) : StreamPlay(sharedPref) {
    override var name = "StreamPlay Anime"

    override suspend fun load(url: String): LoadResponse? {
        return super.load(url)
    }
}
