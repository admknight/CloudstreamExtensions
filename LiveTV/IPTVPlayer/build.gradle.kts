version = 6

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "IPTV Player"
    status = 1
    tvTypes = listOf(         "Live",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/LiveTV/IPTVPlayer/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
