package com.admknight.pelispedia

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PelispediaProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PelispediaProvider())
    }
}
