package com.admknight.animejl

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class AnimeJlProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeJlProvider())
    }
}
