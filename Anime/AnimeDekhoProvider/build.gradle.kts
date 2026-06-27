version = 66

cloudstream {
    language = "hi"
    authors = listOf("Adam Knight")
    description = "Includes AnimeDekho,OnePace(DUB,SUB) and HindiSubAnime"
    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "Cartoon"
    )

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Anime/AnimeDekhoProvider/icon.png"

    isCrossPlatform = true
}
