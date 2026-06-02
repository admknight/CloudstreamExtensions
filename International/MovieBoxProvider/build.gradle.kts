version = 21

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Multi Language Movies and Series Provider"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/MovieBoxProvider/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
