version = 4

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "ToonStream Multi Language"
    status = 1
    tvTypes = listOf("AnimeMovie","Anime","Cartoon")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/Toonstream/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
