package com.admknight.animeflvio

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class AnimeflvIOProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeflvIOProvider())
    }
}
