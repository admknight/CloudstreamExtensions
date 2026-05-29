version = 2

cloudstream {
    authors     = listOf("Phisher98")
    language    = "fil"
    description = "Contains Bluray7"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie","TvSeries")
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Pinoymoviepedia/icon.png"

    isCrossPlatform = true
}

