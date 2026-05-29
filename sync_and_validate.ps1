# Adam Knight's Mega-Repo Sync & Validation Script

$sources = @(
    "https://github.com/Hexated/CloudStream-Extensions.git",
    "https://github.com/phisher98/cloudstream-extensions-phisher.git",
    "https://github.com/Rowdy-Avocado/CloudStream-Extensions.git",
    "https://github.com/rockhero1234/cinephile.git",
    "https://github.com/MegixS/Megix-Repo.git",
    "https://github.com/Stormun/CloudStream-Extensions.git",
    "https://github.com/Sushan64/NetMirror-Extension.git"
)

Write-Host "--- Starting Mega-Sync ---" -ForegroundColor Cyan

if (Test-Path "temp_sources") { Remove-Item -Recurse -Force "temp_sources" }
New-Item -ItemType Directory -Path "temp_sources" | Out-Null

foreach ($repo in $sources) {
    $repoName = [System.IO.Path]::GetFileNameWithoutExtension($repo)
    Write-Host "Cloning $repoName..." -ForegroundColor Yellow
    git clone --depth 1 $repo "temp_sources/$repoName" | Out-Null

    $gradleFiles = Get-ChildItem -Path "temp_sources/$repoName" -Filter "build.gradle.kts" -Recurse | Where-Object { $_.DirectoryName -ne (Get-Item "temp_sources/$repoName").FullName }

    foreach ($file in $gradleFiles) {
        $pluginName = $file.Directory.Name
        Write-Host "Checking $pluginName..."

        # Smart Version Check
        if (Test-Path "$pluginName/build.gradle.kts") {
            $newVer = Select-String -Path $file.FullName -Pattern "^version\s*=\s*([0-9]+)" | ForEach-Object { $_.Matches.Groups[1].Value }
            $oldVer = Select-String -Path "$pluginName/build.gradle.kts" -Pattern "^version\s*=\s*([0-9]+)" | ForEach-Object { $_.Matches.Groups[1].Value }

            if ($newVer -and $oldVer -and ([int]$newVer -le [int]$oldVer)) {
                Write-Host "  v$oldVer already up to date." -ForegroundColor Gray
                continue
            }
        }

        # Copy and Brand
        if (-not (Test-Path "$pluginName")) { New-Item -ItemType Directory -Path "$pluginName" | Out-Null }
        Copy-Item -Path "$($file.Directory.FullName)\*" -Destination "$pluginName" -Recurse -Force

        # Apply Branding
        $gradleContent = Get-Content "$pluginName/build.gradle.kts"
        $gradleContent = $gradleContent -replace 'authors = listOf\(.*\)', 'authors = listOf("Adam Knight")'
        $gradleContent -replace 'iconUrl = ".*"', "iconUrl = `"https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/$pluginName/icon.png`"" | Set-Content "$pluginName/build.gradle.kts"

        # Validate Build
        Write-Host "  Validating $pluginName..." -ForegroundColor Magenta
        .\gradlew.bat "$($pluginName):assembleDebug" --quiet

        if ($LASTEXITCODE -ne 0) {
            Write-Host "  BUILD FAILED! Deleting $pluginName..." -ForegroundColor Red
            Remove-Item -Recurse -Force "$pluginName"
        } else {
            Write-Host "  SUCCESS! Added $pluginName." -ForegroundColor Green
        }
    }
}

Remove-Item -Recurse -Force "temp_sources"
Write-Host "--- Sync Complete! ---" -ForegroundColor Cyan
