$categoryDirs = @("Anime", "Bollywood", "International", "LiveTV", "Tools")
foreach ($cat in $categoryDirs) {
    if (Test-Path $cat) {
        $plugins = Get-ChildItem -Path $cat -Directory
        foreach ($p in $plugins) {
            $pName = $p.Name
            $file = Join-Path $p.FullName "build.gradle.kts"
            $lang = "en"
            if ($cat -eq "Bollywood") { $lang = "hi" }

            $content = @"
cloudstream {
    language = "$lang"
    authors = listOf("Adam Knight")
    status = 1
    iconUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/$cat/$pName/icon.png"
}
"@
            Set-Content $file $content
            Write-Host "Fixed: $cat/$pName"
        }
    }
}
