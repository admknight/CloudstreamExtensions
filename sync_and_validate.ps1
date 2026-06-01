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
            $Code = $Code -replace 'argamap\(', 'runAllAsync('
            $Code = $Code -replace '\.apmap', '.map'

            # Aggressive DSL Migration
            $Code = $Code -replace '\bMovieSearchResponse\(', 'newMovieSearchResponse('
            $Code = $Code -replace '\bTvSeriesSearchResponse\(', 'newTvSeriesSearchResponse('
            $Code = $Code -replace '\bAnimeSearchResponse\(', 'newAnimeSearchResponse('
            $Code = $Code -replace '\bLiveSearchResponse\(', 'newLiveSearchResponse('
            $Code = $Code -replace '\bHomePageResponse\(', 'newHomePageResponse('
            $Code = $Code -replace '\bAnimeLoadResponse\(', 'newAnimeLoadResponse('
            $Code = $Code -replace '\bMovieLoadResponse\(', 'newMovieLoadResponse('
            $Code = $Code -replace '\bTvSeriesLoadResponse\(', 'newTvSeriesLoadResponse('
            $Code = $Code -replace '\bLiveStreamLoadResponse\(', 'newLiveStreamLoadResponse('
            $Code = $Code -replace '\bEpisode\(', 'newEpisode('
            $Code = $Code -replace '\bExtractorLink\(', 'newExtractorLink('

            # Score / Rating Migration
            $Code = $Code -replace 'rating\s*=\s*(.*)\.toRatingInt\(\)', 'score = Score.from10($1)'
            $Code = $Code -replace '\.toRatingInt\(\)', ''

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

# --- GLOBAL DEDUPLICATION & STABILITY PATCHING ---
Write-Host "Applying Global Deduplication & Stability Patches..." -ForegroundColor Cyan

# 1. Resolve Kisskh Redeclaration
Get-ChildItem -Path "International/KisskhProvider" -Filter "*KisskhProvider*.kt" -Recurse | Where-Object { $_.FullName -match "lagradost" } | Remove-Item -Force

# 2. Resolve Netmirror Clashes
Get-ChildItem -Path "Bollywood/Netmirror" -Filter "*.kt" -Recurse | ForEach-Object {
    $c = Get-Content $_.FullName -Raw
    $c = $c -replace '\bEpisode\b', 'NetmirrorEpisode'
    $c = $c -replace 'com\.lagradost\.cloudstream3\.NetmirrorEpisode', 'com.lagradost.cloudstream3.Episode'
    Set-Content $_.FullName $c
}

# 3. Resolve StremioAddon Duplicates
if (Test-Path "Tools/StremioAddon/src/main/kotlin/com/phisher98/StremioAddonProvider.kt") {
    Remove-Item "Tools/StremioAddon/src/main/kotlin/com/phisher98/StremioAddonProvider.kt" -Force
}

# 4. Global Namespace Alignment & Base64 Fixes
Get-ChildItem -Path "." -Filter "*.kt" -Recurse | ForEach-Object {
    $c = Get-Content $_.FullName -Raw
    $c = $c -replace 'import java\.util\.Base64', ''
    $c = $c -replace 'Base64\.getDecoder\(\)\.decode', 'base64DecodeArray'
    Set-Content $_.FullName $c
}

Remove-Item -Path "temp_sources" -Recurse -Force -ErrorAction SilentlyContinue
