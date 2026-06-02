version = 1

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Stream tokusatsu content including Power Ranger, Kamen Rider, Super Sentai, Metal Heroes, and other Japanese special effect series with English subs"
    status = 1
    tvTypes = listOf(         "TvSeries",         "Movie",         "Anime"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/TokuZilla/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
