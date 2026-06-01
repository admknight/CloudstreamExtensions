@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.konan.properties.Properties

dependencies {
    implementation("com.google.android.material:material:1.11.0")
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}

version = 48

cloudstream {
    description = "The ultimate All-in-One home screen to access all of your extensions at one place"
    authors = listOf("Adam Knight")
    status = 2
    tvTypes = listOf("All")
    requiresResources = true
    language = "en"
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Tools/Ultima/icon.png"
    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
