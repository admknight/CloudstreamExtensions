version = 6

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Indian Movies/Series/Kdrama(Hindi Dubbed)"
    status = 1
    tvTypes = listOf(         "AsianDrama",         "TvSeries",         "Movie",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/MPlayerProvider/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
