package com.admknight.bflix

import android.os.Handler
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class BflixProviderPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(BflixProvider())
        registerMainAPI(FmoviesToProvider())
        registerMainAPI(SflixProProvider())
    }

    companion object {
        inline fun Handler.postFunction(crossinline function: () -> Unit) {
            this.post {
                function()
            }
        }
    }
}
