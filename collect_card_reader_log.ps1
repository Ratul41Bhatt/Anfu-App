# collect_card_reader_log.ps1
# Collects live card reader logs from Anfu AF980 terminal and saves them to a file.

param (
    [string]$OutputFile = "",
    [switch]$AllLogs = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       ANFU AF980 CARD READER LIVE LOG COLLECTOR          " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Check ADB connection
$devices = adb devices | Select-String -Pattern "device$"
if (-not $devices) {
    Write-Host "[ERROR] No authorized ADB device found!" -ForegroundColor Red
    Write-Host "Please ensure your Anfu AF980 is connected via USB with USB Debugging enabled." -ForegroundColor Yellow
    exit 1
}

$devSerial = ($devices[0].Line -split "\s+")[0]
$model = (adb -s $devSerial shell getprop ro.product.model).Trim()
$brand = (adb -s $devSerial shell getprop ro.product.brand).Trim()
$androidVer = (adb -s $devSerial shell getprop ro.build.version.release).Trim()

Write-Host "[INFO] Connected Device: $brand $model ($devSerial) Android $androidVer" -ForegroundColor Green

# 2. Expand device logcat buffer to 16MB
adb -s $devSerial logcat -G 16M | Out-Null
Write-Host "[INFO] Device ring buffer expanded to 16MB" -ForegroundColor Green

# 3. Setup output file
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logDir = Join-Path $PSScriptRoot "terminal_logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

if (-not $OutputFile) {
    $OutputFile = Join-Path $logDir "card_reader_AF980_$timestamp.log"
}

Write-Host "[INFO] Logging to file: $OutputFile" -ForegroundColor Yellow
Write-Host "Press Ctrl+C at any time to stop capturing." -ForegroundColor Gray
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan

# Write header to output file
$header = @"
================================================================================
ANFU AF980 CARD READER LOG CAPTURE
Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
Device: $brand $model ($devSerial)
Android: $androidVer
================================================================================
"@
$header | Out-File -FilePath $OutputFile -Encoding UTF8

# Regex pattern for card reader & terminal events:
$cardFilter = "FindCardActivity|PayActivity|CardReaderAccess|SZANFU_AFSDKService|AF_CORE_LIBRARY|CardProcess|EmvProcess|CardBinderService|EmvBinderService|EmvCardNoConfirm|CARDDIAG|CARDTIME|KonaMoney|CSC APDU|kernelProcessResult|apiEmvKernelProcess|openFindCardForEMV|closeFindCardForEMV|Iso8583|Emv55|DynamicPinPad|offlinepaydemo|ReaderRecovery|posWriteWorkingKey|TMK|Fatal|AndroidRuntime"

# Start logcat process
$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = "adb"
$pinfo.Arguments = "-s $devSerial logcat -v time"
$pinfo.RedirectStandardOutput = $true
$pinfo.RedirectStandardError = $true
$pinfo.UseShellExecute = $false
$pinfo.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $pinfo

$fileStream = [System.IO.File]::AppendText($OutputFile)

try {
    [void]$process.Start()
    $stdout = $process.StandardOutput

    while (-not $stdout.EndOfStream) {
        $line = $stdout.ReadLine()
        if ($null -eq $line) { break }

        # Always save every line to the file
        $fileStream.WriteLine($line)

        # In live display, filter for card reader and related events
        if ($AllLogs -or ($line -match $cardFilter)) {
            if ($line -match "kernelProcessResult|apiEmvKernelProcess returned|refused|failed|error|ERR_|Exception|Fatal|null result") {
                Write-Host $line -ForegroundColor Red
            } elseif ($line -match "CARD WAS READ|EmvCardNoConfirm|openFindCardForEMV: arming|KONA CL:|CARDTIME|CARDDIAG") {
                Write-Host $line -ForegroundColor Green
            } elseif ($line -match "CSC APDU|PPSE|additionByte|emvParam|iso8583") {
                Write-Host $line -ForegroundColor Magenta
            } elseif ($line -match "SZANFU_AFSDKService|AF_CORE_LIBRARY") {
                Write-Host $line -ForegroundColor DarkGray
            } else {
                Write-Host $line -ForegroundColor White
            }
        }
    }
} finally {
    if (-not $process.HasExited) {
        $process.Kill()
    }
    $fileStream.Flush()
    $fileStream.Close()
    Write-Host "`n[INFO] Log collection stopped. File saved: $OutputFile" -ForegroundColor Green
}
