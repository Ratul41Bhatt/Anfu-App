@echo off
REM ---------------------------------------------------------------------------
REM  Reads the AFSDKService version off the AF930 - the number Anfu asked for.
REM  Finds adb by itself. Double-click with the terminal connected by USB.
REM  Writes sdk_version.txt next to this file.
REM ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set "OUT=%~dp0sdk_version.txt"
set "PKGS=%TEMP%\af_pkgs.txt"
set "DUMP=%TEMP%\af_dump.txt"
set "ADB="

echo Looking for adb...

REM --- 1. already on PATH? -----------------------------------------------
for /f "delims=" %%A in ('where adb 2^>nul') do if not defined ADB set "ADB=%%A"

REM --- 2. the usual places ------------------------------------------------
if not defined ADB call :try "D:\Development_environment_resource_package\Development_environment_resource_package\platform-tools\adb.exe"
if not defined ADB call :try "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB call :try "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not defined ADB call :try "%ProgramFiles%\Android\Android Studio\plugins\android\lib\adb.exe"
if not defined ADB call :try "C:\platform-tools\adb.exe"
if not defined ADB call :try "D:\platform-tools\adb.exe"
if not defined ADB call :try "%USERPROFILE%\Downloads\platform-tools\adb.exe"

REM --- 3. search a few likely roots ---------------------------------------
if not defined ADB call :search "%LOCALAPPDATA%\Android"
if not defined ADB call :search "D:\Development_environment_resource_package"
if not defined ADB call :search "%USERPROFILE%\Downloads"
if not defined ADB call :search "D:\"
if not defined ADB call :search "C:\Users\%USERNAME%"

if not defined ADB (
    echo.
    echo Could not find adb.exe anywhere I looked.
    echo Search Windows for "adb.exe", then drag its folder into this window
    echo or paste the full path below and re-run.
    echo.
    pause
    exit /b 1
)

echo Using: %ADB%
echo.

> "%OUT%" echo adb: %ADB%
>> "%OUT%" echo.
>> "%OUT%" echo ================ CONNECTED DEVICE ================
"%ADB%" devices -l >> "%OUT%" 2>&1

>> "%OUT%" echo.
>> "%OUT%" echo ================ TERMINAL BUILD =================
for %%P in (ro.product.model ro.product.manufacturer ro.build.display.id ro.build.version.incremental ro.build.version.release) do (
    >> "%OUT%" echo|set /p="%%P = "
    "%ADB%" shell getprop %%P >> "%OUT%" 2>&1
)

"%ADB%" shell pm list packages > "%PKGS%" 2>&1

>> "%OUT%" echo.
>> "%OUT%" echo ============ ANFU / SDK PACKAGES ================
findstr /i "anfu szanfu sdk pos emv pinpad" "%PKGS%" >> "%OUT%" 2>&1

>> "%OUT%" echo.
>> "%OUT%" echo ============ VERSIONS ===========================
for /f "tokens=2 delims=:" %%p in ('findstr /i "anfu szanfu sdk" "%PKGS%"') do (
    >> "%OUT%" echo.
    >> "%OUT%" echo --- %%p
    "%ADB%" shell dumpsys package %%p > "%DUMP%" 2>&1
    findstr /i "versionName versionCode firstInstallTime lastUpdateTime" "%DUMP%" >> "%OUT%" 2>&1
)

>> "%OUT%" echo.
>> "%OUT%" echo ============ OUR APP ============================
"%ADB%" shell dumpsys package com.anfu.offline.pay.demo.test > "%DUMP%" 2>&1
findstr /i "versionName versionCode" "%DUMP%" >> "%OUT%" 2>&1

del "%PKGS%" >nul 2>&1
del "%DUMP%" >nul 2>&1

echo Done. Wrote:
echo   %OUT%
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
echo   searching %~1 ...
for /f "delims=" %%F in ('dir /s /b "%~1\adb.exe" 2^>nul') do if not defined ADB set "ADB=%%F"
goto :eof
