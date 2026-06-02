package com.admknight.animepahe

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import android.content.Context

enum class ServerList(val link: Pair<String, Boolean>) {
    SI("https://animepahe.pw" to true),
    ORG("https://animepahe.org" to true),
    BEST("https://animepahe.com" to true)
}

@CloudstreamPlugin
class AnimePaheProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimePahe())
        registerExtractorAPI(Kwik())
        registerExtractorAPI(Pahe())

        this.openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = BottomFragment(this)
            frag.show(activity.supportFragmentManager, "AnimePaheSettings")
        }
    }

    companion object {
        var currentAnimepaheServer: String
            get() = getKey("ANIMEPAHE_CURRENT_SERVER") ?: ServerList.BEST.link.first
            set(value) {
                setKey("ANIMEPAHE_CURRENT_SERVER", value)
            }
    }
}
