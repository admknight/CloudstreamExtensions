version = 1

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Movies and Series (Mostly 720p)"
    status = 1
    tvTypes = listOf("Movie","TvSeries")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Goojara/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
