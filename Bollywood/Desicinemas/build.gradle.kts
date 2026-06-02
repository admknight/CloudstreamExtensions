version = 12

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Contains BollyZone"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/Desicinemas/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
