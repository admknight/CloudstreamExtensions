package com.admknight.estrenosdoramas

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class EstrenosDoramasProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(EstrenosDoramasProvider())
    }
}
