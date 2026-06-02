version = 3

cloudstream {
    language = "bn"
    authors = listOf("Adam Knight")
    description = "Bangla/Hindi Movies/Series"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",         "Anime"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/Cinefreak/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
