@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 646

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        android.buildFeatures.buildConfig=true
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
    implementation("com.google.android.material:material:1.14.0")
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

     description = "#1 best extention based on MultiAPI"
     authors = listOf("Adam Knight")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Anime",
        "Movie",
        "Cartoon",
        "AnimeMovie"
    )

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/StreamPlay/icon.png"

    requiresResources = true
    isCrossPlatform = false

}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.browser:browser:1.10.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
