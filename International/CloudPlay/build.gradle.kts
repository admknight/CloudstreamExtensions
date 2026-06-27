@file:Suppress("UnstableApiUsage")

version = 4

android {
    defaultConfig {
        android.buildFeatures.buildConfig = true
    }
}

cloudstream {
    language = "en"
    requiresResources = false
    description = "CloudPlay Live TV Extension"
    authors = listOf("Adam Knight")

    status = 1
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/CloudPlay/icon.png"

    isCrossPlatform = false
}
