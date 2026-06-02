version = 21

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Lorem Ipsum"
    status = 1
    tvTypes = listOf(         "AnimeMovie",         "Anime",         "OVA",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Kickassanime/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
