import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 14

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {

        buildConfigField("String", "ANICHI_API", "\"\"")
        buildConfigField("String", "ANICHI_SERVER", "\"\"")
        buildConfigField("String", "ANICHI_ENDPOINT", "\"\"")
        buildConfigField("String", "ANICHI_APP", "\"\"")
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
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
        "AnimeMovie",
        "Anime",
        "OVA",
    )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/Anichi/icon.png"

    isCrossPlatform = true
}