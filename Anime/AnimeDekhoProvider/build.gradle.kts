version = 62

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Includes AnimeDekho,OnePace(DUB,SUB) and HindiSubAnime"
    status = 1
    tvTypes = listOf(         "AnimeMovie",         "Anime",         "Cartoon"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/AnimeDekhoProvider/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
