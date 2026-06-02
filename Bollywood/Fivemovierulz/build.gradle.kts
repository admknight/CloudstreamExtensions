version = 4

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Movies & Series"
    status = 1
    tvTypes = listOf(         "TvSeries",         "Movie"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/Fivemovierulz/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
