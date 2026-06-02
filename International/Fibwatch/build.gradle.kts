version = 5

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries"      )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Fibwatch/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
