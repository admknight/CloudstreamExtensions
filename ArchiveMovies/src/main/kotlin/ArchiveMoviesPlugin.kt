package com.admknight.archivemovies

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ArchiveMoviesPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ArchiveMoviesProvider())
    }
}
