version = 8

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Webview is used to load links, reload if necessary"
    status = 1
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/BflixProvider/icon.png"
}

android {
    namespace = "com.admknight.bflix"
    buildFeatures {
        buildConfig = true
    }
}
