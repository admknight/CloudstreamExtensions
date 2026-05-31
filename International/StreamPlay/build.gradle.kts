@file:Suppress("UnstableApiUsage")

version = 644

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        buildConfigField("String", "TMDB_API", "\"\"")
        buildConfigField("String", "ZSHOW_API", "\"\"")
        buildConfigField("String", "ANICHI_API", "\"\"")
        buildConfigField("String", "KissKh", "\"\"")
        buildConfigField("String", "KisskhSub", "\"\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"\"")
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"\"")
        buildConfigField("String", "PROXYAPI", "\"\"")
        buildConfigField("String", "KAISVA", "\"\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"\"")
        buildConfigField("String", "KAIMEG", "\"\"")
        buildConfigField("String", "KAIDEC", "\"\"")
        buildConfigField("String", "KAIENC", "\"\"")
        buildConfigField("String", "VideasyDEC", "\"\"")
        buildConfigField("String", "YFXENC", "\"\"")
        buildConfigField("String", "YFXDEC", "\"\"")
        buildConfigField("String", "NuvFeb", "\"\"")
        buildConfigField("String", "ANICHI_APP", "\"\"")
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.browser:browser:1.8.0")
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}

cloudstream {
    language = "en"
    description = "#1 best extention based on MultiAPI"
    authors = listOf("Adam Knight")
    status = 1
    tvTypes = listOf("AsianDrama", "TvSeries", "Anime", "Movie", "Cartoon", "AnimeMovie")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/StreamPlay/icon.png"
    requiresResources = true
    isCrossPlatform = false
}
