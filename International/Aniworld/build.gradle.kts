version = 9

cloudstream {
    language = "de"
    authors = listOf("Adam Knight")
    description = "Include: Serienstream (Login Required under Extension Settings)"
    status = 1
    tvTypes = listOf(         "AnimeMovie",         "Anime",         "OVA",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Aniworld/icon.png"
    requiresResources = true
    isCrossPlatform = false
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
}

android {
    buildFeatures {
        buildConfig = true
    }
}
