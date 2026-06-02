cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    version = 1
    description = "Includes many providers with the same layout as Vidstream"
    status = 1
    tvTypes = listOf(
        "Anime",
        "Movie",
        "AnimeMovie",
        "TvSeries"
    )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/VidstreamBundle/icon.png"
}

android {
    namespace = "com.admknight.vidstreambundle"
    buildFeatures {
        buildConfig = true
    }
}
