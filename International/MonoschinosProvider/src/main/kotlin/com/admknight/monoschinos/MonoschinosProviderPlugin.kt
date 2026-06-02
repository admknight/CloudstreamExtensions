package com.admknight.monoschinos

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MonoschinosProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MonoschinosProvider())
    }
}
