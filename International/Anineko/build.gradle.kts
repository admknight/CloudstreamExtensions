// use an integer for version numbers
version = 2


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anime from Anineko"
    authors = listOf("Adam Knight")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")
    language = "en"
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Anineko/icon.png"
    isCrossPlatform = true
}
