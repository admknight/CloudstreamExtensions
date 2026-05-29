// use an integer for version numbers
version = 2

cloudstream {
    language = "ta"
    // All of these properties are optional, you can safely remove them
    description = "Movies (Tamil)"
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
        "Movies",
    )

    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/Tamilian/icon.png"

    isCrossPlatform = false

}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
