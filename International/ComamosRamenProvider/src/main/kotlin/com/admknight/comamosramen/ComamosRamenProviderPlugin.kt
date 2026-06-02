package com.admknight.comamosramen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ComamosRamenProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ComamosRamenProvider())
    }
}
