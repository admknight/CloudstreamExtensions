package com.admknight.pelisplusso

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PelisplusSOProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PelisplusSOProvider())
    }
}
