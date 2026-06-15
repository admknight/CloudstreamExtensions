// use an integer for version numbers
version = 9


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anime from all-wish.me"
    authors = listOf("Adam Knight")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("All")

    language = "en"

    // random cc logo i found
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/AllWish/icon.png"

    isCrossPlatform = true
}
