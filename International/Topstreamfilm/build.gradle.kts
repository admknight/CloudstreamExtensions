version = 6

cloudstream {
    language = "de"
    authors = listOf("Adam Knight")
    description = "Filme  and Serien (German)"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Topstreamfilm/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
