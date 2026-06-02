version = 5

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Watch Movies & TvSeries (Multi-Lang)"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/DudeFilms/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
