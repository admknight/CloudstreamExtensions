$manifests = Get-ChildItem -Path . -Filter AndroidManifest.xml -Recurse
foreach ($m in $manifests) {
    $Content = Get-Content $m.FullName -Raw
    # Remove the package="..." attribute completely
    $NewContent = $Content -replace '\s*package="[^"]*"', ''
    if ($Content -ne $NewContent) {
        Set-Content $m.FullName $NewContent
        Write-Host "Fixed Manifest: $($m.FullName)"
    }
}
