version = 43

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Netflix, PrimeVideo, Disney+ Hotstar Contents in Multiple Languages"
    status = 1
    tvTypes = listOf(         "Movie",         "TvSeries"     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/Netmirror/icon.png"
}

dependencies {
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}

android {
    buildFeatures {
        buildConfig = true
    }
}
