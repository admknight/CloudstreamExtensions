import org.jetbrains.kotlin.konan.properties.Properties

version = 440
android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "SIMKL_API", "\"${""
        buildConfigField("String", "TMDB_KEY", "\"${""
        buildConfigField("String", "CC_COOKIE", "\"${""
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

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/CineStream/icon.png"
}
