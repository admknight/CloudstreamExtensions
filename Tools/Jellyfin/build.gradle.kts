version = 3

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Jellyfin is a Free Software Media System that puts you in control of managing and streaming your media"
    status = 1
    tvTypes = listOf(         "AsianDrama",         "TvSeries",         "Anime",         "Movie",         "Cartoon",         "AnimeMovie"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Tools/Jellyfin/icon.png"
    requiresResources = true
    isCrossPlatform = false
}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.12.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}

android {
    buildFeatures {
        buildConfig = true
    }
}
