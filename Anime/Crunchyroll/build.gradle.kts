version = 5

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "The Crunchyroll provider allows you to watch all the shows that are on Crunchyroll."
    status = 0
    tvTypes = listOf("AnimeMovie", "Anime", "OVA")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/Crunchyroll/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
    }
}
