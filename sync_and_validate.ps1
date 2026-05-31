# Mega-Repo Surgical Sync & Sanitization Script v3
# This script organizes, brands, AND REPAIRS 151+ plugins for Adam Knight.

$Sources = @(
    "https://github.com/Hexated/CloudStream-Extensions.git",
    "https://github.com/phisher98/cloudstream-extensions-phisher.git",
    "https://github.com/rockhero1234/cinephile.git",
    "https://github.com/SaurabhKaperwan/CSX.git",
    "https://github.com/Sushan64/NetMirror-Extension.git",
    "https://github.com/Stormunblessed/storm-ext.git"
)

$AnimeRegex = "Anichi|AllWish|AnimePahe|Animeav1|AnimeCloud|AnimeDekho|Animedubhindi|Animekhor|Animexin|Gogo|NineAnime|Crunchyroll|Tokusatsu|TokuZilla|Animekisa|Kawaiifu|Aniflix|DubbedAnime|AsiaFlix|KimCartoon|Topcartoon|Toon|OnePace|Donghua|DoraBash|Latanime|Tenshi|Wco|Zoro"
$HindiRegex = "Netmirror|Cinevood|Mp4Moviez|Tamil|Skymovies|Bolly|Vega|Moviesmod|CineStream|AllMovieLand|Hindmovie|HDhub4u|FourKHDHub|MovieBlast|Fivemovierulz|Movierulzhd|Cinefreak|Bangla|OnlineMoviesHindi|MoviesDrive|Desicinemas|UHDmovies"
$ToolsRegex = "Ultima|Stremio|Jellyfin|BingedReview|RingZ|GDIndex"
$LiveRegex = "IPTV|QuickIPTV|Sports|EjaTv|Tvtwofourseven"

if (-not (Test-Path "temp_sources")) { New-Item -ItemType Directory "temp_sources" | Out-Null }

foreach ($URL in $Sources) {
    $RepoName = $URL.Split('/')[-1].Replace(".git", "")
    $Path = Join-Path "temp_sources" $RepoName
    if (-not (Test-Path $Path)) {
        Write-Host "Cloning $RepoName..." -ForegroundColor Cyan
        git clone --depth 1 $URL $Path | Out-Null
    }

    Get-ChildItem -Path $Path -Directory -Recurse | Where-Object { Test-Path (Join-Path $_.FullName "build.gradle.kts") } | ForEach-Object {
        $PluginDir = $_
        $PluginName = $_.Name
        if ($PluginName -match "^(gradle|buildSrc|build|.github|.idea)$") { return }

        $Category = "International"
        if ($PluginName -match $AnimeRegex) { $Category = "Anime" }
        elseif ($PluginName -match $HindiRegex) { $Category = "Bollywood" }
        elseif ($PluginName -match $ToolsRegex) { $Category = "Tools" }
        elseif ($PluginName -match $LiveRegex) { $Category = "LiveTV" }

        $Target = Join-Path $Category $PluginName
        if (-not (Test-Path $Target)) { New-Item -ItemType Directory $Target -Force | Out-Null }

        Copy-Item -Path (Join-Path $PluginDir.FullName "*") -Destination $Target -Recurse -Force -ErrorAction SilentlyContinue

        # --- CODE SURGERY ---
        Get-ChildItem -Path $Target -Filter "*.kt" -Recurse | ForEach-Object {
            $Code = Get-Content $_.FullName -Raw
            $Code = $Code -replace '\.apmap', '.map'
            $Code = $Code -replace 'MovieSearchResponse\(', 'newMovieSearchResponse('
            $Code = $Code -replace 'TvSeriesSearchResponse\(', 'newTvSeriesSearchResponse('
            $Code = $Code -replace 'AnimeSearchResponse\(', 'newAnimeSearchResponse('
            Set-Content $_.FullName $Code
        }

        # --- GRADLE SURGERY ---
        $GradleFile = Join-Path $Target "build.gradle.kts"
        if (Test-Path $GradleFile) {
            $Content = Get-Content $GradleFile
            $Content = $Content -replace 'authors\s*=\s*listOf\(.*\)', 'authors = listOf("Adam Knight")'

            # SAFE buildConfigField Stripping
            $Content = [regex]::Replace($Content, 'buildConfigField\s*\(\s*"String"\s*,\s*"([^"]+)"\s*,.*?\)', 'buildConfigField("String", "$1", "\"\"")')

            $Content = $Content -replace 'val properties = Properties\(\).*properties\.load\(.*\)', ''

            if ($Content -notmatch "buildConfig = true") {
                $Content = $Content -replace 'buildFeatures \{', "buildFeatures {`n        buildConfig = true"
            }

            $Content = $Content -replace 'iconUrl\s*=\s*"[^"]+"', "iconUrl = `"https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/$Category/$PluginName/icon.png`""

            $Content | Set-Content $GradleFile
        }

        Write-Host "Repaired: [$Category] $PluginName" -ForegroundColor Green
    }
}
