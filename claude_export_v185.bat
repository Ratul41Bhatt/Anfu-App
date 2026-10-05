@echo off
REM Exports all .java sources as .txt copies so Claude can read them.
REM Safe: it only COPIES files into _claude_src. Your real sources are untouched.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "$root='D:\projectv1.85\projectv1\offlinepaydemo';" ^
 "$src=Join-Path $root 'app\src\main\java';" ^
 "$dst=Join-Path $root '_claude_src';" ^
 "if(Test-Path $dst){Remove-Item $dst -Recurse -Force};" ^
 "New-Item -ItemType Directory -Path $dst | Out-Null;" ^
 "Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $rel = $_.FullName.Substring($src.Length+1) -replace '\\','__'; Copy-Item $_.FullName (Join-Path $dst ($rel + '.txt')) };" ^
 "Write-Host ('Exported ' + (Get-ChildItem $dst).Count + ' files to ' + $dst)"
echo.
echo Done. You can close this window.
pause
