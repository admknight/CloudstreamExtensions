package com.admknight.pelisplushd

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PelisplusHDProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PelisplusHDProvider())
    }
}
