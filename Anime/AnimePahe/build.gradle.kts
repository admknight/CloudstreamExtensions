version = 24

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Animes (SUB/DUB)"
    status = 1
    tvTypes = listOf(         "AnimeMovie",         "Anime",         "OVA",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/AnimePahe/icon.png"
    requiresResources = true
    isCrossPlatform = false
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}

android {
    namespace = "com.admknight.animepahe"
    buildFeatures {
        buildConfig = true
    }
}
