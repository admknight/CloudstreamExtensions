version = 1

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",         "Anime"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Zinkmovies/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
