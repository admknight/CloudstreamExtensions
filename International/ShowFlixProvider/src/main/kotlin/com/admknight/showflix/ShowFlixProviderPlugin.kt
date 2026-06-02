package com.admknight.showflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ShowFlixProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ShowFlixProvider())
        
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(Showflixupnshare())
        registerExtractorAPI(Rubyvidhub())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Showflixarchives())
    }
}
