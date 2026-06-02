// use an integer for version numbers
version = 6

cloudstream {
    description ="Filme  and Serien (German)"
    authors = listOf("Adam Knight")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    language = "de"

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/International/Topstreamfilm/icon.png"

    isCrossPlatform = true
}
