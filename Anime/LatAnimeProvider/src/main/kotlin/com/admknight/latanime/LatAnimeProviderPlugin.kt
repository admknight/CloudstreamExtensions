package com.admknight.latanime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LatAnimeProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(LatAnimeProvider())
    }
<<<<<<<< HEAD:International/LatAnimeProvider/src/main/kotlin/com/stormunblessed/LatAnimeProviderPlugin.kt
}
========
}
>>>>>>>> dbb850a (Restore core plugins and fix namespace/DSL errors. 114+ plugins verified locally.):Anime/LatAnimeProvider/src/main/kotlin/com/admknight/latanime/LatAnimeProviderPlugin.kt
