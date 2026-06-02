version = 9

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Watch Movies & TvSeries (Multi-Lang)"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/Hindmoviez/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
