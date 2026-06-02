version = 7

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Anime/Cartoon in Hindi"
    status = 1
    tvTypes = listOf(         "AnimeMovie",         "Anime",         "Cartoon"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Animesalt/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
