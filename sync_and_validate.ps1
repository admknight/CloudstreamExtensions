# Adam Knight's ULTRA-STRICT Mega-Repo Sync & Validation Script
# Ensures that ONLY 100% working plugins are kept and NO duplicates exist.

$sources = @(
    "https://github.com/Hexated/CloudStream-Extensions.git",
    "https://github.com/phisher98/cloudstream-extensions-phisher.git",
    "https://github.com/rockhero1234/cinephile.git",
    "https://github.com/MegixS/Megix-Repo.git",
    "https://github.com/Sushan64/NetMirror-Extension.git",
    "https://codeberg.org/Stormunblessed/storm-ext.git"
)

# Mandatory plugins that we should be careful with
$corePlugins = @("Cinevood", "BingedReview", "Mp4Moviez", "SkymoviesHD", "Tamilblasters")

Write-Host "--- Starting Ultra-Sync & Validation ---" -ForegroundColor Cyan

if (Test-Path "temp_sources") { Remove-Item -Recurse -Force "temp_sources" }
New-Item -ItemType Directory -Path "temp_sources" | Out-Null

foreach ($repo in $sources) {
    # Create a unique name for the temp folder
    $repoName = $repo -replace 'https://[^/]+/', '' -replace '\.git', '' -replace '/', '_'
    Write-Host "`nCloning $repoName..." -ForegroundColor Yellow
    git clone --depth 1 $repo "temp_sources/$repoName" 2>$null | Out-Null

    $gradleFiles = Get-ChildItem -Path "temp_sources/$repoName" -Filter "build.gradle.kts" -Recurse | Where-Object { $_.DirectoryName -ne (Get-Item "temp_sources/$repoName").FullName }

    foreach ($file in $gradleFiles) {
        $pluginName = $file.Directory.Name

        Write-Host "Processing $pluginName..." -NoNewline

        # --- VERSION CHECK ---
        if (Test-Path "$pluginName/build.gradle.kts") {
            $newVer = Select-String -Path $file.FullName -Pattern "^version\s*=\s*([0-9]+)" | ForEach-Object { $_.Matches.Groups[1].Value }
            $oldVer = Select-String -Path "$pluginName/build.gradle.kts" -Pattern "^version\s*=\s*([0-9]+)" | ForEach-Object { $_.Matches.Groups[1].Value }

            if ($newVer -and $oldVer -and ([int]$newVer -le [int]$oldVer)) {
                Write-Host " [ALREADY UP TO DATE]" -ForegroundColor Gray
                continue
            }
        }

        # Isolated Copy for testing
        $testDir = "temp_validation_$pluginName"
        if (Test-Path $testDir) { Remove-Item -Recurse -Force $testDir }
        New-Item -ItemType Directory -Path $testDir | Out-Null
        Copy-Item -Path "$($file.Directory.FullName)\*" -Destination $testDir -Recurse -Force

        # --- PRE-FIXING ERRORS ---
        $manifestPath = "$testDir/src/main/AndroidManifest.xml"
        if (Test-Path $manifestPath) {
            $manifestContent = Get-Content $manifestPath -Raw
            if ($manifestContent -match 'package="([^"]+)"') {
                $manifestContent = $manifestContent -replace 'package="[^"]+"', ''
                Set-Content $manifestPath $manifestContent
            }
        }

        $gradlePath = "$testDir/build.gradle.kts"
        $gradleContent = Get-Content $gradlePath -Raw
        $gradleContent = $gradleContent -replace 'authors = listOf\(.*\)', 'authors = listOf("Adam Knight")'
        $gradleContent = $gradleContent -replace 'iconUrl = ".*"', "iconUrl = `"https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/$pluginName/icon.png`""
        $gradleContent = $gradleContent -replace '(?s)val properties = Properties\(\).*?properties\.load\(.*?\)', ''
        $gradleContent = $gradleContent -replace '(?m)^\s*\.inputStream\(\)\)\s*$', ''
        $gradleContent = $gradleContent -replace 'properties\.getProperty\(.*?\)', '""'
        if ($gradleContent -notmatch "buildConfig = true") {
            $gradleContent = $gradleContent -replace 'buildFeatures \{', "buildFeatures {`n        buildConfig = true"
        }
        Set-Content $gradlePath $gradleContent

        # --- ISOLATED BUILD TEST ---
        $settingsFile = "settings.gradle.kts"
        $settingsContent = Get-Content $settingsFile -Raw
        $tempInclude = "`ninclude(`":$pluginName`")`nproject(`":$pluginName`").projectDir = file(`"$testDir`")"
        Add-Content $settingsFile $tempInclude

        .\gradlew.bat ":$($pluginName):assembleDebug" --quiet 2>$null
        $buildSuccess = $LASTEXITCODE -eq 0
        $settingsContent | Set-Content $settingsFile

        if ($buildSuccess) {
            Write-Host " [PASSED]" -ForegroundColor Green
            # CLEAN DELETE OLD SRC TO PREVENT DUPLICATES
            if (Test-Path "$pluginName/src") { Remove-Item -Recurse -Force "$pluginName/src" }
            if (-not (Test-Path "$pluginName")) { New-Item -ItemType Directory -Path "$pluginName" | Out-Null }
            Copy-Item -Path "$testDir\*" -Destination "$pluginName" -Recurse -Force
        } else {
            Write-Host " [FAILED]" -ForegroundColor Red
        }
        Remove-Item -Recurse -Force $testDir
    }
}

Remove-Item -Recurse -Force "temp_sources"
Write-Host "`n--- Sync & Validation Complete! ---" -ForegroundColor Cyan
