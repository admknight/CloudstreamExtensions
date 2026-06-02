version = 2

cloudstream {
    language = "te"
    authors = listOf("Adam Knight")
    description = "MovieBlast App"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/MovieBlast/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
