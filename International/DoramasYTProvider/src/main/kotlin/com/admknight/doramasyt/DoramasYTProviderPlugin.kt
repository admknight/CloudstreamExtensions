package com.admknight.doramasyt

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DoramasYTProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DoramasYTProvider())
    }
}
