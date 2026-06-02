version = 14

cloudstream {
}

android {
    namespace = "com.admknight.anichi"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "ANICHI_API", "\"https://api.allmanga.to/graphql\"")
        buildConfigField("String", "ANICHI_APP", "\"https://allmanga.to\"")
        buildConfigField("String", "ANICHI_ENDPOINT", "\"https://api.allmanga.to\"")
    }
}
