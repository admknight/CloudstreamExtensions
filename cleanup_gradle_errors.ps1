Get-ChildItem -Filter "build.gradle.kts" -Recurse | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match '\.inputStream\(\)\)') {
        Write-Host "Cleaning dangling inputStream in: $($_.FullName)"
        # Remove the dangling .inputStream()) that was left behind by the previous script
        $content = $content -replace '(?m)^\s*\.inputStream\(\)\)\s*$', ''
        Set-Content $_.FullName $content
    }
}
