// use an integer for version numbers
version = 5

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Animes"
    authors = listOf("Adam Knight")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")
    language = "en"
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/AniDb/icon.png"
    isCrossPlatform = false
    requiresResources = true
}
