package com.admknight.jellyfin

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class JellyfinPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("Jellyfin", Context.MODE_PRIVATE)
        registerMainAPI(Jellyfin(sharedPref))
        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "SettingsFragment")
        }
    }
}



