package recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class Full4MoviesPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Full4MoviesProvider())
    }
}
