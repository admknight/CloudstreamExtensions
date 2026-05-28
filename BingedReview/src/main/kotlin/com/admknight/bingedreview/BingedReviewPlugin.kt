package com.admknight.bingedreview

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BingedReviewPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BingedProvider())
    }
}
