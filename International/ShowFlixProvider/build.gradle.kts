version = 11

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "HD Provider for all Indian Languages"
    status = 1
    tvTypes = listOf(         "TvSeries",         "Movie",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/ShowFlixProvider/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
