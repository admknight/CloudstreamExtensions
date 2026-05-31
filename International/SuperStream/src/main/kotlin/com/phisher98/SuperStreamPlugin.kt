package com.admknight.superstream

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.admknight.superstream.settings.SettingsFragment
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SuperStreamPlugin: BasePlugin() {
    override fun load() {
        val sharedPref = context!!.getSharedPreferences("SuperStream", Context.MODE_PRIVATE)
        registerMainAPI(SuperStream(sharedPref))

        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}



