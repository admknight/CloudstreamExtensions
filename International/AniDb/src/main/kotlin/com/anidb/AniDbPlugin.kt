package com.anidb

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AniDbPlugin : Plugin() {
    override fun load() {
        registerMainAPI(AniDb())

        this.openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = AniDbSettingsFragment(this)
            frag.show(activity.supportFragmentManager, "")
        }
    }

    companion object {
        /** Full cookie string saved after a successful WebView CF bypass (e.g. "cf_clearance=abc; __ddg2_=xyz") */
        var cfCookies: String
            get() = getKey("ANIDB_CF_COOKIES") ?: ""
            set(value) {
                setKey("ANIDB_CF_COOKIES", value)
            }

        /** The exact User-Agent string used by the WebView to solve the challenge. */
        var cfUserAgent: String
            get() = getKey("ANIDB_CF_USER_AGENT") ?: ""
            set(value) {
                setKey("ANIDB_CF_USER_AGENT", value)
            }

        /** The host for which cfCookies were captured (e.g. "https://anidb.app") */
        var cfCookieHost: String
            get() = getKey("ANIDB_CF_COOKIE_HOST") ?: ""
            set(value) {
                setKey("ANIDB_CF_COOKIE_HOST", value)
            }
    }
}
