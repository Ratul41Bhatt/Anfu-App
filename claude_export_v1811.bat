@echo off
REM ---------------------------------------------------------------------------
REM  Exports V1.8.11's sources as .txt into this folder's _claude_src.
REM
REM  Claude cannot read app\src\main\java\com\hjq\demo\ui\activity\*.java
REM  directly: that is nine folders below the connected folder and the file
REM  bridge stops at seven. _claude_src sits one below, so a flattened .txt
REM  copy there is readable.
REM
REM  Copies only. Nothing under app\src is modified.
REM ---------------------------------------------------------------------------
setlocal
set ROOT=%~dp0
if %ROOT:~-1%==\ set ROOT=%ROOT:~0,-1%

echo Exporting from: %ROOT%
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "$root='%ROOT%';" ^
 "$src=Join-Path $root 'app\src\main\java';" ^
 "$dst=Join-Path $root '_claude_src';" ^
 "if(-not (Test-Path $src)){Write-Host 'No app\src\main\java here - is this the project root?'; exit 1};" ^
 "if(Test-Path $dst){Remove-Item $dst -Recurse -Force};" ^
 "New-Item -ItemType Directory -Path $dst | Out-Null;" ^
 "Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $rel = $_.FullName.Substring($src.Length+1) -replace '\\','__'; Copy-Item $_.FullName (Join-Path $dst ($rel + '.txt')) };" ^
 "Copy-Item (Join-Path $root 'app\src\main\assets\*.json') $dst -ErrorAction SilentlyContinue;" ^
 "Write-Host ('  exported ' + (Get-ChildItem $dst).Count + ' files to _claude_src')"

echo.
echo Done - tell Claude it is ready.
pause
