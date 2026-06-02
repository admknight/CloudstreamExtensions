version = 3

cloudstream {
    language = "ko"
    authors = listOf("Adam Knight")
    description = "Anime and movies with Korean subtitles only (no Korean audio)"
    status = 1
    tvTypes = listOf(         "AsianDrama",         "TvSeries",         "Movie",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/OHLI24/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
