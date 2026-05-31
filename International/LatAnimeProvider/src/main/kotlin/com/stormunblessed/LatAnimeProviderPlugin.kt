package com.admknight.latanime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LatAnimeProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LatAnimeProvider())
    }
}
