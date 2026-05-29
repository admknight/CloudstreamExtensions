Get-ChildItem -Filter "build.gradle.kts" -Recurse | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match 'properties\.load') {
        Write-Host "Fixing: $($_.FullName)"
        # Remove properties loading block
        $content = $content -replace '(?s)val properties = Properties\(\).*?properties\.load\(.*?\)', ''
        # Replace property lookups with empty string
        $content = $content -replace 'properties\.getProperty\(.*?\)', '""'
        Set-Content $_.FullName $content
    }
}
