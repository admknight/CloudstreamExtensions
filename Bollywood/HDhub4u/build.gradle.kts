// use an integer for version numbers
version = 48


cloudstream {
    authors = listOf("Adam Knight")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime"
    )
    language = "hi"
//  https://t0.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://hdhub4u.gratis&size=64
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Bollywood/HDhub4u/icon.png"

    isCrossPlatform = false
}
