version = 2

cloudstream {
    language = "ta"
    authors = listOf("Adam Knight")
    description = "Movies (Tamil)"
    status = 1
    tvTypes = listOf(         "Movies",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/Tamilian/icon.png"
    isCrossPlatform = false
}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}

android {
    buildFeatures {
        buildConfig = true
    }
}
