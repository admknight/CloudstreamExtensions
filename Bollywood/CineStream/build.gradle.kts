import org.jetbrains.kotlin.konan.properties.Properties

version = 480
android {
    defaultConfig {
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "SIMKL_API", "\"\"")
        buildConfigField("String", "TMDB_KEY", "\"\"")
        buildConfigField("String", "CC_COOKIE", "\"\"")
        buildConfigField("String", "CASTLE_KEY", "\"\"")
        buildConfigField("String", "MOVIEBLAST_TOKEN", "\"\"")
        buildConfigField("String", "MOVIEBLAST_API", "\"\"")
        buildConfigField("String", "MOVIEBLAST_KEY", "\"\"")
        buildConfigField("String", "NETMIRROR_TOKEN", "\"\"")
    }
}

cloudstream {
    language = "en"
    description = "One stop solution for Movies, Series, Anime, AsianDrama and Torrents"
    authors = listOf("Adam Knight")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama",
        "Anime",
        "Torrent"
    )

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/CineStream/icon.png"
}
