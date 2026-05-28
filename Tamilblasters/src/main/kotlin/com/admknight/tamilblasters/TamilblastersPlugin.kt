package com.admknight.tamilblasters

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TamilblastersPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TamilblastersProvider())
    }
}
