version = 18

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Indian MultiLanguage Provider (Mostly Hindi)"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",         "Cartoon"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/AllMovieLandProvider/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
