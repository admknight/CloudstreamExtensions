$categoryDirs = @("Anime", "Bollywood", "International", "LiveTV", "Tools")
foreach ($cat in $categoryDirs) {
    if (Test-Path $cat) {
        $plugins = Get-ChildItem -Path $cat -Directory
        foreach ($p in $plugins) {
            $pName = $p.Name
            $CleanName = $pName.ToLower().Replace(" ", "").Replace("-", "").Replace("_", "").Replace("provider", "")
            $NewPackage = "com.admknight.$CleanName"

            $ktFiles = Get-ChildItem -Path $p.FullName -Filter *.kt -Recurse
            foreach ($file in $ktFiles) {
                $Content = Get-Content $file.FullName -Raw

                # Update package declaration
                $Content = $Content -replace '(?m)^package\s+[a-zA-Z0-9\.]+', "package $NewPackage"

                # Update BuildConfig imports and other internal references
                $Content = $Content -replace 'com\.phisher98', $NewPackage
                $Content = $Content -replace 'com\.stormunblessed', $NewPackage

                # Specifically fix BuildConfig if it was from com.lagradost (common for some providers)
                $Content = $Content -replace 'import\s+com\.lagradost\.(?!cloudstream3|api)[a-zA-Z0-9\.]+\.BuildConfig', "import $NewPackage.BuildConfig"

                Set-Content $file.FullName $Content
            }
            Write-Host "Fixed Packages in: $cat/$pName to $NewPackage"
        }
    }
}
