$bytes = [System.IO.File]::ReadAllBytes('d:\projectv1.85\projectv1\offlinepaydemo V1.8.11\offlinepaydemo\szanfu_apk_extracted\classes.dex')
$chars = [char[]]::new($bytes.Length)
for ($i = 0; $i -lt $bytes.Length; $i++) {
    $b = $bytes[$i]
    if ($b -ge 32 -and $b -le 126) {
        $chars[$i] = [char]$b
    } else {
        $chars[$i] = ' '
    }
}
$text = [string]::new($chars)
$regex = [regex]::new(".{0,80}closeFindCardForEMV.{0,150}")
$matches = $regex.Matches($text)
foreach ($m in $matches) {
    Write-Output $m.Value
    Write-Output "---"
}
