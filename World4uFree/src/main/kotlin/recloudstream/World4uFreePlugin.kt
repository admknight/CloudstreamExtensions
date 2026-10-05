package recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class World4uFreePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(World4uFreeProvider())
    }
}
