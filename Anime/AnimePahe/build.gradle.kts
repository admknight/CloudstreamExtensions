// use an integer for version numbers
version = 26

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Animes (SUB/DUB)"
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
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/AnimePahe/icon.png"

    requiresResources = true
    isCrossPlatform = false
}
