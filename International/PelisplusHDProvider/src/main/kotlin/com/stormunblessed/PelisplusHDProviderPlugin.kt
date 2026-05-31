package com.admknight.pelisplushd

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class PelisplusHDProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(PelisplusHDProvider())
    }
}
