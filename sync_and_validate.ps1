# Mega-Repo Surgical Sync & Sanitization Script
# This script organizes, brands, and fixes 151+ plugins for Adam Knight.

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

        # Determine Category
        $Category = "International"
        if ($PluginName -match $AnimeRegex) { $Category = "Anime" }
        elseif ($PluginName -match $HindiRegex) { $Category = "Bollywood" }
        elseif ($PluginName -match $ToolsRegex) { $Category = "Tools" }
        elseif ($PluginName -match $LiveRegex) { $Category = "LiveTV" }

        $Target = Join-Path $Category $PluginName
        $SrcDir = Join-Path $Target "src"
        if (-not (Test-Path $Target)) { New-Item -ItemType Directory $Target -Force | Out-Null }

        # Copy data
        Copy-Item -Path (Join-Path $PluginDir.FullName "*") -Destination $Target -Recurse -Force -ErrorAction SilentlyContinue

        # --- SURGERY & BRANDING ---
        $GradleFile = Join-Path $Target "build.gradle.kts"
        if (Test-Path $GradleFile) {
            $Content = Get-Content $GradleFile

            # Brand authors
            $Content = $Content -replace 'authors\s*=\s*listOf\(.*\)', 'authors = listOf("Adam Knight")'

            # Sanitization: Neutralize private properties without breaking syntax
            $Content = $Content -replace 'val properties = Properties\(\).*properties\.load\(.*\)', ''
            $Content = $Content -replace 'properties\.getProperty\("[^"]*"\)', '""'
            $Content = $Content -replace 'properties\.getProperty\([^)]*\)', '""'

            # Fix common template syntax errors (unclosed strings)
            $Content = $Content -replace '\"\$\{""', '""'

            # Force BuildConfig for metadata access
            if ($Content -notmatch "buildConfig = true") {
                $Content = $Content -replace 'buildFeatures \{', "buildFeatures {`n        buildConfig = true"
            }

            # Local Icon Mirroring
            $IconUrlMatch = [regex]::Match($Content, 'iconUrl\s*=\s*"([^"]+)"')
            if ($IconUrlMatch.Success) {
                $OriginalIcon = $IconUrlMatch.Groups[1].Value
                Invoke-WebRequest -Uri $OriginalIcon -OutFile (Join-Path $Target "icon.png") -ErrorAction SilentlyContinue
                $Content = $Content -replace 'iconUrl\s*=\s*"[^"]+"', "iconUrl = `"https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/$Category/$PluginName/icon.png`""
            }

            $Content | Set-Content $GradleFile
        }

        # Fix AndroidManifest package errors
        $Manifest = Join-Path $Target "src/main/AndroidManifest.xml"
        if (Test-Path $Manifest) {
            $MContent = Get-Content $Manifest
            $MContent = $MContent -replace 'package="[^"]*"', ""
            $MContent | Set-Content $Manifest
        }

        Write-Host "Synced & Sanitized: [$Category] $PluginName" -ForegroundColor Green
    }
}
