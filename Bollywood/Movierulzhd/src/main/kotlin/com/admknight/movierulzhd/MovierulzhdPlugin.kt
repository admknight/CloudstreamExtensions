package com.admknight.movierulzhd

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey

@CloudstreamPlugin
class MovierulzhdPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Movierulzhd())
        registerMainAPI(Hdmovie2())
        registerExtractorAPI(FMHD())
    }

    data class Domains(val movierulzhd: String? = null)

    companion object {
        fun getDomains(): Domains? {
            return getKey("MOVierulzhd_DOMAINS")
        }
    }
}
