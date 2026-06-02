version = 11

cloudstream {
    language = "id"
    authors = listOf("Adam Knight")
    description = "Lorem Ipsum"
    status = 1
    tvTypes = listOf(         "TvSeries",         "Movie",         "Anime",         "AsianDrama",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/IdlixProvider/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
