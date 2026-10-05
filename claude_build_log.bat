@echo off
REM ---------------------------------------------------------------------------
REM  Gives Claude the two things it cannot get on its own:
REM    1. a .txt copy of every .java source  (Claude cannot read .java directly)
REM    2. the javac error lines from a failed build
REM  Both land in _claude_src\. Nothing in app\src is modified - it only copies.
REM ---------------------------------------------------------------------------
setlocal
set ROOT=D:\projectv1.85\projectv1\offlinepaydemo
cd /d "%ROOT%"

echo [1/2] Exporting sources...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "$root='%ROOT%';" ^
 "$src=Join-Path $root 'app\src\main\java';" ^
 "$dst=Join-Path $root '_claude_src';" ^
 "if(Test-Path $dst){Remove-Item $dst -Recurse -Force};" ^
 "New-Item -ItemType Directory -Path $dst | Out-Null;" ^
 "Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $rel = $_.FullName.Substring($src.Length+1) -replace '\\','__'; Copy-Item $_.FullName (Join-Path $dst ($rel + '.txt')) };" ^
 "Write-Host ('  exported ' + (Get-ChildItem $dst).Count + ' files')"

echo.
echo [2/2] Compiling - about a minute...
call gradlew.bat :app:compileDebugJavaWithJavac --console=plain > "%ROOT%\_claude_src\_build_output.txt" 2>&1

echo.
echo ================= COMPILER ERRORS =================
findstr /C:"error:" "%ROOT%\_claude_src\_build_output.txt"
echo ===================================================
echo.
echo Full log: _claude_src\_build_output.txt
echo Tell Claude it is ready.
pause
