version = 1

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Asian Dramas"
    status = 1
    tvTypes = listOf(         "AsianDrama",         "TvSeries",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/OneTouchTV/icon.png"
    isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
    }
}
