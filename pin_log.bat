@echo off
REM ---------------------------------------------------------------------------
REM  Captures what the PIN pad reports for each key.
REM  1. Plug the terminal in and double-click this.
REM  2. When it says READY, run a sale on the terminal.
REM  3. On the PIN screen press:  1  2  3  9  then CLEAR  then CLEAR
REM  4. Come back here and press any key.
REM  It writes pin_log.txt next to this file.
REM ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set "OUT=%~dp0pin_log.txt"
set "ADB="

for /f "delims=" %%A in ('where adb 2^>nul') do if not defined ADB set "ADB=%%A"
if not defined ADB call :try "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB call :try "D:\Development_environment_resource_package\Development_environment_resource_package\platform-tools\adb.exe"
if not defined ADB call :search "%LOCALAPPDATA%\Android"
if not defined ADB call :search "D:\"
if not defined ADB call :search "C:\Users\%USERNAME%"

if not defined ADB (
    echo Could not find adb.exe. Search Windows for adb.exe and tell Claude the folder.
    pause
    exit /b 1
)

echo Using adb: %ADB%
"%ADB%" logcat -c >nul 2>&1

echo.
echo ============================================================
echo   READY - now run a sale on the terminal.
echo.
echo   On the PIN screen press:   1  2  3  9   CLEAR   CLEAR
echo.
echo   Then come back and press any key here.
echo ============================================================
echo.
pause

"%ADB%" logcat -d -s DynamicPinPad:* > "%OUT%" 2>&1

echo.
echo Wrote %OUT%
echo.
type "%OUT%"
echo.
pause
exit /b 0

:try
if exist %1 set "ADB=%~1"
goto :eof

:search
if not exist %1 goto :eof
for /f "delims=" %%F in ('dir /s /b "%~1\adb.exe" 2^>nul') do if not defined ADB set "ADB=%%F"
goto :eof
