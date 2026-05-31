package com.admknight.nginx

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class NginxProviderPlugin : Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("Nginx", Context.MODE_PRIVATE)
        
        NginxProvider.overrideUrl = sharedPref.getString("nginx_url", null)
        val user = sharedPref.getString("nginx_user", "")
        val pass = sharedPref.getString("nginx_pass", "")
        NginxProvider.loginCredentials = if (user.isNullOrBlank() && pass.isNullOrBlank()) null else "$user:$pass"

        registerMainAPI(NginxProvider())

        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = NginxSettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "NginxSettings")
        }
    }
}




