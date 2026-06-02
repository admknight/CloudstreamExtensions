version = 10

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Lorem Ipsum"
    status = 1
    tvTypes = listOf(         "AnimeMovie",         "TvSeries",         "Movie",     )
    requiresResources = true
}

dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
}

android {
    buildFeatures {
        buildConfig = true
    }
}
