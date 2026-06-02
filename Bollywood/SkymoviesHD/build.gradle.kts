version = 1

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "just testing"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",         "NSFW"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/SkymoviesHD/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
    }
}
