import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 24

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TMDB_API", "\"\"")
    }
}

dependencies {
    implementation("com.google.android.material:material:1.13.0")
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

     description = "[!] Requires Setup \n- StremioX allows you to use stream addons \n- StremioC allows you to use catalog addons"
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
        "TvSeries",
        "Movie",
    )
    requiresResources = true
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Tools/StremioX/icon.png"
}
