import java.util.Properties

val properties = Properties()
val propertiesFile = File(rootDir, "local.properties")
if (propertiesFile.exists()) {
    properties.load(propertiesFile.inputStream())
}

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    status = 1
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/CineStream/icon.png"
}

android {
    defaultConfig {
        buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API") ?: ""}\"")
        buildConfigField("String", "TMDB_KEY", "\"${properties.getProperty("TMDB_KEY") ?: ""}\"")
        buildConfigField("String", "CC_COOKIE", "\"${properties.getProperty("CC_COOKIE") ?: ""}\"")
    }
}
