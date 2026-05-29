@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 33

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TMDB_API", "\"${""
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"${""
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"${""
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"${""
        buildConfigField("String", "CatflixAPI", "\"${""
        buildConfigField("String", "NuvFeb", "\"${""
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them
     description = "SuperStream (Retrieve the cookie using Login with Google to properly utilize SuperStream."
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

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/SuperStream/icon.png"

    requiresResources = true
    isCrossPlatform = false

}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.leanback:leanback:1.2.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
