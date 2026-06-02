version = 8

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Watch Movies & TvSeries (Multi-Lang/Audio)"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Cinemacity/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
