version = 20

cloudstream {
    language = "en"
    authors = listOf("Adam Knight")
    description = "Watch 9anime with the help of the Consumet API."
    status = 1
    tvTypes = listOf(         "Anime",         "OVA",     )
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/AniwaveProvider/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
    }
}
