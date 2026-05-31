package com.admknight.entrepeliculasyseries

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class EntrepeliculasyseriesProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(EntrepeliculasyseriesProvider())
    }
}
