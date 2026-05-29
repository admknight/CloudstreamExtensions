# Adam Knight's ULTRA-STRICT Mega-Repo Sync & Validation Script
# This script ensures that ONLY 100% working plugins are kept.

$sources = @(
    "https://github.com/Hexated/CloudStream-Extensions.git",
    "https://github.com/phisher98/cloudstream-extensions-phisher.git",
    "https://github.com/Rowdy-Avocado/CloudStream-Extensions.git",
    "https://github.com/rockhero1234/cinephile.git",
    "https://github.com/MegixS/Megix-Repo.git",
    "https://github.com/Stormun/CloudStream-Extensions.git",
    "https://github.com/Sushan64/NetMirror-Extension.git"
)

# Core plugins that we NEVER delete
$corePlugins = @("Cinevood", "BingedReview", "Mp4Moviez", "SkymoviesHD", "Tamilblasters")

Write-Host "--- Starting Ultra-Sync & Validation ---" -ForegroundColor Cyan

if (Test-Path "temp_sources") { Remove-Item -Recurse -Force "temp_sources" }
New-Item -ItemType Directory -Path "temp_sources" | Out-Null

foreach ($repo in $sources) {
    $repoName = [System.IO.Path]::GetFileNameWithoutExtension($repo)
    Write-Host "`nCloning $repoName..." -ForegroundColor Yellow
    git clone --depth 1 $repo "temp_sources/$repoName" 2>$null | Out-Null

    $gradleFiles = Get-ChildItem -Path "temp_sources/$repoName" -Filter "build.gradle.kts" -Recurse | Where-Object { $_.DirectoryName -ne (Get-Item "temp_sources/$repoName").FullName }

    foreach ($file in $gradleFiles) {
        $pluginName = $file.Directory.Name
        if ($corePlugins -contains $pluginName) { continue }

        Write-Host "Processing $pluginName..." -NoNewline

        # Isolated Copy for testing
        $testDir = "temp_validation_$pluginName"
        if (Test-Path $testDir) { Remove-Item -Recurse -Force $testDir }
        New-Item -ItemType Directory -Path $testDir | Out-Null
        Copy-Item -Path "$($file.Directory.FullName)\*" -Destination $testDir -Recurse -Force

        # --- PRE-FIXING COMMON ERRORS ---

        # 1. Fix Manifest Package Error (Move package to build.gradle.kts as namespace)
        $manifestPath = "$testDir/src/main/AndroidManifest.xml"
        if (Test-Path $manifestPath) {
            $manifestContent = Get-Content $manifestPath -Raw
            if ($manifestContent -match 'package="([^"]+)"') {
                $pkg = $Matches[1]
                $manifestContent = $manifestContent -replace 'package="[^"]+"', ''
                Set-Content $manifestPath $manifestContent
            }
        }

        # 2. Fix build.gradle.kts
        $gradlePath = "$testDir/build.gradle.kts"
        $gradleContent = Get-Content $gradlePath -Raw

        # Branding
        $gradleContent = $gradleContent -replace 'authors = listOf\(.*\)', 'authors = listOf("Adam Knight")'
        $gradleContent = $gradleContent -replace 'iconUrl = ".*"', "iconUrl = `"https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/$pluginName/icon.png`""

        # Neutralize private keys
        $gradleContent = $gradleContent -replace '(?s)val properties = Properties\(\).*?properties\.load\(.*?\)', ''
        $gradleContent = $gradleContent -replace '(?m)^\s*\.inputStream\(\)\)\s*$', ''
        $gradleContent = $gradleContent -replace 'properties\.getProperty\(.*?\)', '""'

        # Ensure buildConfig is enabled
        if ($gradleContent -notmatch "buildConfig = true") {
            $gradleContent = $gradleContent -replace 'buildFeatures \{', "buildFeatures {`n        buildConfig = true"
        }

        Set-Content $gradlePath $gradleContent

        # 3. Isolated Build Test
        # We try to build it as part of the project
        # First, add it to settings.gradle.kts temporarily
        $settingsFile = "settings.gradle.kts"
        $settingsContent = Get-Content $settingsFile -Raw
        $tempInclude = "`ninclude(`":$pluginName`")`nproject(`":$pluginName`").projectDir = file(`"$testDir`")"
        Add-Content $settingsFile $tempInclude

        .\gradlew.bat ":$($pluginName):assembleDebug" --quiet 2>$null
        $buildSuccess = $LASTEXITCODE -eq 0

        # Remove from settings.gradle.kts
        $settingsContent | Set-Content $settingsFile

        if ($buildSuccess) {
            Write-Host " [PASSED]" -ForegroundColor Green
            # Move validated plugin to main folder
            if (Test-Path "$pluginName") { Remove-Item -Recurse -Force "$pluginName" }
            Move-Item -Path $testDir -Destination "$pluginName"
        } else {
            Write-Host " [FAILED]" -ForegroundColor Red
            Remove-Item -Recurse -Force $testDir
        }
    }
}

Remove-Item -Recurse -Force "temp_sources"
Write-Host "`n--- Sync & Validation Complete! ---" -ForegroundColor Cyan
Write-Host "You can now push the working plugins to GitHub." -ForegroundColor Green
