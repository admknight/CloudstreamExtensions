# Emergency Repair Script for Gradle Syntax Errors
# This script finds corrupted buildConfigField lines and fixes them globally.

$categories = @("Anime", "Bollywood", "International", "LiveTV", "Tools")

foreach ($cat in $categories) {
    if (-not (Test-Path $cat)) { continue }

    Get-ChildItem -Path $cat -Filter "build.gradle.kts" -Recurse | ForEach-Object {
        $file = $_.FullName
        $content = Get-Content $file -Raw

        # Pattern 1: buildConfigField("String", "KEY", "\""}"\"")
        $content = $content -replace 'buildConfigField\s*\(\s*"String"\s*,\s*"[^"]+"\s*,\s*"\\"[^"]+"\\"\s*\)', 'buildConfigField("String", "$1", "\"\"")'

        # Generic fix for buildConfigField to ensure they always end in a clean empty string
        # Match anything inside buildConfigField and force the 3rd argument to be ""
        $newContent = [regex]::Replace($content, 'buildConfigField\s*\(\s*"String"\s*,\s*"([^"]+)"\s*,.*?\)', 'buildConfigField("String", "$1", "\"\"")')

        # Fix viewBinding if it was broken
        $newContent = $newContent -replace 'viewBinding = true\s*}', "viewBinding = true`n    }"

        if ($newContent -ne $content) {
            $newContent | Set-Content $file
            Write-Host "Fixed syntax in: $file" -ForegroundColor Green
        }
    }
}
