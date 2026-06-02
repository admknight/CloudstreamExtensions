version = 2

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Allows you to use stremio addons. Requires setup. (Torrents and old api does not work)"
    status = 1
    tvTypes = listOf("Others")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Tools/StremioProvider/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
    }
}
