version = 4

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Uses TMDB"
    status = 1
    tvTypes = listOf(         "TvSeries",         "Movie",         "AnimeMovie"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Ask4Movie/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
    }
}
