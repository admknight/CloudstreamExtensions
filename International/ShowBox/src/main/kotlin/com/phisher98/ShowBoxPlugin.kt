
package com.admknight.showbox

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class ShowBoxPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("ShowBox", Context.MODE_PRIVATE)
        val api = ShowBox(sharedPref) // pass context
        registerMainAPI(api)
        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}




