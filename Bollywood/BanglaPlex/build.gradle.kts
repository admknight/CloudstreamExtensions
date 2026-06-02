version = 3

cloudstream {
    language = "bn"
    authors = listOf("Adam Knight")
    description = "BanglaPlex"
    status = 1
    tvTypes = listOf("Movie","TvSeries")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/BanglaPlex/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
