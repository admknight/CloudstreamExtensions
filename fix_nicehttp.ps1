Get-ChildItem -Path . -Filter *.kt -Recurse | ForEach-Object {
    $Content = Get-Content $_.FullName -Raw
    # Fix RequestBodyTypes imports
    $NewContent = $Content -replace 'import\s+com\.admknight\.[a-z0-9]+\.RequestBodyTypes', "import com.lagradost.nicehttp.RequestBodyTypes"
    if ($Content -ne $NewContent) {
        Set-Content $_.FullName $NewContent
        Write-Host "Fixed NiceHttp import in: $($_.FullName)"
    }
}
