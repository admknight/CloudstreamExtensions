package com.admknight.torrastream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TorraStreamProvider: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("TorraStream", Context.MODE_PRIVATE)
        registerMainAPI(TorraStream(sharedPref))
    }
}
