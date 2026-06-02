version = 11

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Due to the video host changing encryption methods extremely often these extensions might not work perfectly. Also includes Dopebox, Solarmovie, Zoro, HDToday and 2embed"
    status = 1
    tvTypes = listOf(         "TvSeries",         "Movie",         "Anime",         "AnimeMovie",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/SflixProvider/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
    }
}
