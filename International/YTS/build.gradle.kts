version = 8

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "YTS Movies 4K Support (Torrent)"
    status = 1
    tvTypes = listOf("Movie","Torrent")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/YTS/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
