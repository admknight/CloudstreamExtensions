package com.lagradost

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class NginxProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NginxProvider())
        
        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = NginxSettingsFragment(this)
            frag.show(activity.supportFragmentManager, "NginxSettings")
        }
    }
}
