version = 2

cloudstream {
    language = "id"
    authors = listOf("Adam Knight")
    description = "PMSM (Pencuri Movie Sub Malay)"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Pmsm/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
