package com.admknight.torrastream

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class TorraStreamAnime(sharedPref: SharedPreferences) : TorraStream(sharedPref) {
    override var name = "TorraStream Anime"
    
    override suspend fun load(url: String): LoadResponse? {
        return super.load(url)
    }
}
