$path = 'd:\projectv1.85\projectv1\offlinepaydemo\app\src\main\java\com\hjq\demo\ui\activity\AmountActivity.java'
$cleanPath = 'd:\projectv1.85\projectv1\offlinepaydemo\clean_section.txt'
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$cleanSection = [System.IO.File]::ReadAllText($cleanPath, [System.Text.Encoding]::UTF8)

$startMarker = "        String icData = sbIcData.toString() + sbMissingTAG.toString();"
$endMarker = "            //User enters PIN"

$startIndex = $content.IndexOf($startMarker)
$endIndex = $content.IndexOf($endMarker)

if ($startIndex -ge 0 -and $endIndex -ge 0) {
    $newContent = $content.Substring(0, $startIndex) + $cleanSection + [Environment]::NewLine + [Environment]::NewLine + "            " + $content.Substring($endIndex)
    [System.IO.File]::WriteAllText($path, $newContent, [System.Text.Encoding]::UTF8)
    Write-Output "Replaced successfully!"
} else {
    Write-Output "Markers not found: start=$startIndex, end=$endIndex"
}
