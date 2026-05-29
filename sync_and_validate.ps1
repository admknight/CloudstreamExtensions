# Adam Knight's Mega-Repo Sync & Validation Script with Categorization

$sources = @(
    "https://github.com/Hexated/CloudStream-Extensions.git",
    "https://github.com/phisher98/cloudstream-extensions-phisher.git",
    "https://github.com/rockhero1234/cinephile.git",
    "https://github.com/SaurabhKaperwan/CSX.git",
    "https://github.com/Sushan64/NetMirror-Extension.git",
    "https://github.com/Stormunblessed/storm-ext.git"
)

# Category Mappings
$animeNames = "Anichi|AllWish|AnimePahe|Animeav1|AnimeCloud|AnimeDekho|Animedubhindi|Animekhor|Animexin|Gogo|NineAnime|Crunchyroll|Tokusatsu|TokuZilla|Animekisa|Kawaiifu|Aniflix|DubbedAnime|AsiaFlix|KimCartoon|Topcartoon|Toon|OnePace|Donghua|DoraBash|Latanime"
$hindiNames = "Cinevood|Mp4Moviez|Tamil|Skymovies|Bolly|Vega|Moviesmod|CineStream|AllMovieLand|Hindmovie|HDhub4u|FourKHDHub|MovieBlast|Fivemovierulz|Movierulzhd|Cinefreak|Bangla|OnlineMoviesHindi|MoviesDrive"
$toolNames = "Netmirror|Ultima|Stremio|Jellyfin|BingedReview|RingZ|GDIndex"
$liveNames = "IPTV|QuickIPTV|Sports|EjaTv|Tvtwofourseven"

function Get-Category($name) {
    if ($name -match $animeNames) { return "Anime" }
    if ($name -match $hindiNames) { return "Bollywood" }
    if ($name -match $toolNames) { return "Tools" }
    if ($name -match $liveNames) { return "LiveTV" }
    return "International"
}

Write-Host "--- Starting Categorized Sync ---" -ForegroundColor Cyan

if (Test-Path "temp_sources") { Remove-Item -Recurse -Force "temp_sources" }
New-Item -ItemType Directory -Path "temp_sources" | Out-Null

foreach ($repo in $sources) {
    $repoName = $repo -replace 'https://[^/]+/', '' -replace '\.git', '' -replace '/', '_'
    Write-Host "`nCloning $repoName..." -ForegroundColor Yellow
    git clone --depth 1 $repo "temp_sources/$repoName" 2>$null | Out-Null

    $gradleFiles = Get-ChildItem -Path "temp_sources/$repoName" -Filter "build.gradle.kts" -Recurse | Where-Object { $_.DirectoryName -ne (Get-Item "temp_sources/$repoName").FullName }

    foreach ($file in $gradleFiles) {
        $pluginName = $file.Directory.Name
        $category = Get-Category $pluginName
        $targetDir = "$category/$pluginName"

        Write-Host "[$category] $pluginName..." -NoNewline

        # Version Check
        if (Test-Path "$targetDir/build.gradle.kts") {
            $newVer = Select-String -Path $file.FullName -Pattern "^version\s*=\s*([0-9]+)" | ForEach-Object { $_.Matches.Groups[1].Value }
            $oldVer = Select-String -Path "$targetDir/build.gradle.kts" -Pattern "^version\s*=\s*([0-9]+)" | ForEach-Object { $_.Matches.Groups[1].Value }
            if ($newVer -and $oldVer -and ([int]$newVer -le [int]$oldVer)) { Write-Host " [UP TO DATE]" -ForegroundColor Gray; continue }
        }

        # Isolated Test Dir
        $testDir = "temp_val_$pluginName"
        if (Test-Path $testDir) { Remove-Item -Recurse -Force $testDir }
        Copy-Item -Path "$($file.Directory.FullName)\*" -Destination $testDir -Recurse -Force

        # Surgery
        $gradlePath = "$testDir/build.gradle.kts"
        $gradleContent = Get-Content $gradlePath -Raw
        $gradleContent = $gradleContent -replace 'authors = listOf\(.*\)', 'authors = listOf("Adam Knight")'
        $gradleContent = $gradleContent -replace 'iconUrl = ".*"', "iconUrl = `"https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/$category/$pluginName/icon.png`""
        $gradleContent = $gradleContent -replace '(?s)val properties = Properties\(\).*?properties\.load\(.*?\)', ''
        $gradleContent = $gradleContent -replace '(?m)^\s*\.inputStream\(\)\)\s*$', ''
        $gradleContent = $gradleContent -replace 'properties\.getProperty\(.*?\)', '""'
        if ($gradleContent -notmatch "buildConfig = true") { $gradleContent = $gradleContent -replace 'buildFeatures \{', "buildFeatures {`n        buildConfig = true" }
        Set-Content $gradlePath $gradleContent

        # Build Test
        $settingsFile = "settings.gradle.kts"
        $origSettings = Get-Content $settingsFile -Raw
        Add-Content $settingsFile "`ninclude(`":$pluginName`")`nproject(`":$pluginName`").projectDir = file(`"$testDir`")"

        .\gradlew.bat ":$($pluginName):assembleDebug" --quiet 2>$null
        $success = $LASTEXITCODE -eq 0
        $origSettings | Set-Content $settingsFile

        if ($success) {
            Write-Host " [PASSED]" -ForegroundColor Green
            if (-not (Test-Path "$category")) { New-Item -ItemType Directory -Path "$category" | Out-Null }
            if (Test-Path "$targetDir") { Remove-Item -Recurse -Force "$targetDir" }
            Move-Item -Path $testDir -Destination "$targetDir" -Force
        } else {
            Write-Host " [FAILED]" -ForegroundColor Red
            Remove-Item -Recurse -Force $testDir
        }
    }
}

Remove-Item -Recurse -Force "temp_sources"
Write-Host "`n--- Categorization Sync Complete! ---" -ForegroundColor Cyan
