package recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class India4MoviesPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(India4MoviesProvider())
    }
}
