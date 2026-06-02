version = 33

cloudstream {
}

android {
    namespace = "com.admknight.superstream"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"https://third.superstream.me\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"https://fourth.superstream.me\"")
        buildConfigField("String", "NuvFeb", "\"https://feb.superstream.me\"")
    }
}
