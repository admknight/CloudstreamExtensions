package com.admknight.doramasflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DoramasFlixProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DoramasFlixProvider())
    }
}
