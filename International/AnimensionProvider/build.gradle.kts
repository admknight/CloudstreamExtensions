version = 1

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Webview is used to load links, reload if necessary"
    status = 1
    tvTypes = listOf(         "Anime",         "OVA",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/AnimensionProvider/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
    }
}
