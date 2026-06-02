package com.admknight.bflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BflixProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BflixProvider())
        registerMainAPI(FmoviesToProvider())
        registerMainAPI(SflixProProvider())
    }
}
