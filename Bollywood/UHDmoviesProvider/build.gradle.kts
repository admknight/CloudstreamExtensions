version = 34

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Indian Multi-language 4K Provider"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/UHDmoviesProvider/icon.png"
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
