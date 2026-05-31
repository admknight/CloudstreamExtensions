package com.admknight.pelisplusso

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class PelisplusSOProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(PelisplusSOProvider())
    }
}
