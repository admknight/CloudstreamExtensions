package recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class CinevoodPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(CinevoodProvider())
    }
}
