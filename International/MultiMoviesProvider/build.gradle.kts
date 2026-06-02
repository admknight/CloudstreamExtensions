version = 48

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Indian Multi-language HD Provider"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",         "Anime",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/MultiMoviesProvider/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
