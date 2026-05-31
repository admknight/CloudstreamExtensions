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

                # Update imports of other classes that were in the original package
                # Patterns to catch: com.phisher98, com.stormunblessed, com.Anichi, com.allwish, com.redowan, etc.
                # We want to replace "import com.Something.Rest" with "import com.admknight.pluginname.Rest"
                # EXCEPT for com.lagradost.cloudstream3 and com.google etc.

                # Use a regex that matches common older package roots
                $Content = $Content -replace 'import\s+com\.(?!lagradost\.(?:cloudstream3|api)|google|android|fasterxml|admknight)[a-zA-Z0-9\.]+\.([A-Z])', "import $NewPackage.`$1"
                $Content = $Content -replace 'com\.phisher98', $NewPackage
                $Content = $Content -replace 'com\.stormunblessed', $NewPackage
                $Content = $Content -replace 'com\.Anichi', $NewPackage
                $Content = $Content -replace 'com\.allwish', $NewPackage

                Set-Content $file.FullName $Content
            }
            Write-Host "Fixed Packages in: $cat/$pName to $NewPackage"
        }
    }
}
