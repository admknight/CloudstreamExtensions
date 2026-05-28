package com.admknight.mp4moviez

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Mp4MoviezPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Mp4MoviezProvider())
    }
}
