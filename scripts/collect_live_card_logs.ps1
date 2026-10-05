# Live Log Collector for Anfu Android POS Terminal
# Automatically captures full logcat and filtered card reading logs in real time.

param (
    [string]$LogsDir = "$PSScriptRoot\..\logs"
)

if (-not (Test-Path $LogsDir)) {
    New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$FullLogPath = Join-Path $LogsDir "live_full_$Timestamp.log"
$CardLogPath = Join-Path $LogsDir "live_card_$Timestamp.log"

Write-Host "============================================================"
Write-Host " ANFU POS Live Log Collector"
Write-Host " Device Target : MediaTek / Anfu AF980 / AF930"
Write-Host " Full Log      : $FullLogPath"
Write-Host " Card Log      : $CardLogPath"
Write-Host "============================================================"

# Ensure buffer size is maximized
adb logcat -G 16M 2>&1 | Out-Null

$CardPattern = "FindCardActivity|PayActivity|AmountActivity|DynamicPinPad|kernelProcessResult|apiEmvKernelProcess|apiEmvGetKernelProcessResult|emvGetKernelProgressResult|openFindCardForEMV|closeFindCardForEMV|EmvProcess|EmvCompletion|AppSelect|SelectProcess|CmdSendRecvApdu|DAL_|Kernel|ERR_|9F2A|AID=|PPSE|2PAY\.SYS|CAPK|cardType|OnlineHost|AF_CORE|SZANFU|CardNative|CardProcess|Iso8583|OfflinePacket|TxnRecord|onCard|onFail|onError|posWrite|PinBlock|GAC|Exception|Fatal|sciDetect|icc|picc|msr"
$NoisePattern = "findCardAsync\s+state\s+Stat\s+ret\s+:\s+:\s+32|cardStatus-->\s+32|DAL_sciDetect failed|KeypadMsgThreadSendRcv|SurfaceFlinger|BufferQueue|BufferStateLayer|hwcomposer|libPerfCtl|BLASTBufferQueue"

$cardRegex = [regex]::new($CardPattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
$noiseRegex = [regex]::new($NoisePattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)

$fullStream = [System.IO.StreamWriter]::new($FullLogPath, $false, [System.Text.Encoding]::UTF8)
$cardStream = [System.IO.StreamWriter]::new($CardLogPath, $false, [System.Text.Encoding]::UTF8)
$fullStream.AutoFlush = $true
$cardStream.AutoFlush = $true

$startMsg = "=== LOGGING STARTED AT $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ==="
$fullStream.WriteLine($startMsg)
$cardStream.WriteLine($startMsg)

Write-Host $startMsg
Write-Host "Ready for card testing. Tap, insert, or swipe card on terminal..."

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "adb"
$psi.Arguments = "logcat -v threadtime"
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)

try {
    while (-not $proc.HasExited) {
        $line = $proc.StandardOutput.ReadLine()
        if ($null -eq $line) {
            Start-Sleep -Milliseconds 50
            continue
        }

        $fullStream.WriteLine($line)

        if ($cardRegex.IsMatch($line) -and -not $noiseRegex.IsMatch($line)) {
            $cardStream.WriteLine($line)
            Write-Host $line
        }
    }
}
finally {
    if (-not $proc.HasExited) {
        $proc.Kill()
    }
    $fullStream.WriteLine("=== LOGGING ENDED AT $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===")
    $cardStream.WriteLine("=== LOGGING ENDED AT $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===")
    $fullStream.Close()
    $cardStream.Close()
}
