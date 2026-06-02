version = 6

cloudstream {
    language = "ta"
    authors = listOf("Adam Knight")
    description = "Indian Multi-language Music Provider"
    status = 1
    tvTypes = listOf(         "Music","Movie"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/MassTamilanProvider/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
