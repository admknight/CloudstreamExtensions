version = 2

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "KayiFamilyTv has Turkish Drama and Documentaries with English / Spanish Subtitles."
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "Documentary")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/KayiFamilyTv/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
