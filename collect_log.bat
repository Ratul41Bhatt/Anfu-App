@echo off
setlocal enabledelayedexpansion
title ANFU AF930 - transaction log capture

REM ---------------------------------------------------------------------------
REM  Captures a full logcat for one transaction and writes two files:
REM
REM    logs\anfu_full_<timestamp>.txt   - everything, including the radio and
REM                                       system buffers. Send this to Anfu.
REM    logs\anfu_emv_<timestamp>.txt    - only the card / EMV kernel lines, with
REM                                       the graphics and card-poll spam
REM                                       stripped out. Send this to Claude.
REM
REM  No PATH setup needed - adb.exe is called by its full path.
REM ---------------------------------------------------------------------------

set "ADB=D:\Development_environment_resource_package\Development_environment_resource_package\platform-tools\adb.exe"
set "OUTDIR=%~dp0logs"

if not exist "%ADB%" (
    echo.
    echo   [X] adb.exe not found at:
    echo       %ADB%
    echo.
    echo   Edit the ADB= line at the top of this file to point at your
    echo   platform-tools folder.
    echo.
    pause
    exit /b 1
)

if not exist "%OUTDIR%" mkdir "%OUTDIR%"

echo.
echo  ============================================================
echo   ANFU AF930  -  transaction log capture
echo  ============================================================
echo.
echo  Checking for the terminal...
"%ADB%" start-server >nul 2>&1
"%ADB%" devices

set "DEVCOUNT=0"
for /f "skip=1 tokens=1,2" %%a in ('"%ADB%" devices') do (
    if "%%b"=="device" set /a DEVCOUNT+=1
)
if "%DEVCOUNT%"=="0" (
    echo.
    echo   [X] No terminal is connected.
    echo.
    echo   Check, in order:
    echo     1. USB cable is in a data port ^(not a charge-only port^).
    echo     2. On the terminal: Settings ^> About ^> tap Build number 7 times,
    echo        then Settings ^> Developer options ^> USB debugging = ON.
    echo     3. Accept the "Allow USB debugging?" prompt on the terminal screen.
    echo        If the list above says "unauthorized", that prompt is waiting.
    echo.
    pause
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"

set "FULL=%OUTDIR%\anfu_full_%TS%.txt"
set "EMV=%OUTDIR%\anfu_emv_%TS%.txt"

REM -- Give the ring buffer room so a long transaction is not truncated. --
"%ADB%" logcat -G 16M >nul 2>&1

echo.
echo  Clearing the old log...
"%ADB%" logcat -c >nul 2>&1
"%ADB%" logcat -b all -c >nul 2>&1

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format HH:mm:ss"') do set "T0=%%i"

echo.
echo  ############################################################
echo  #                                                          #
echo  #   THE LOG IS NOW RECORDING.  ^(cleared at %T0%^)         #
echo  #                                                          #
echo  #   Go to the terminal and run the WHOLE transaction:      #
echo  #                                                          #
echo  #     1. Start the sale / enter the amount                 #
echo  #     2. TAP THE CARD                                      #
echo  #     3. Let it show whatever it shows - error, receipt,   #
echo  #        "insert or swipe", anything                       #
echo  #                                                          #
echo  #   Do NOT press a key here until step 3 has happened.     #
echo  #   Pressing early captures an idle terminal and nothing   #
echo  #   useful is recorded.                                    #
echo  #                                                          #
echo  ############################################################
echo.
pause

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format HH:mm:ss"') do set "T1=%%i"

echo.
echo  Pulling the log ^(recorded %T0% to %T1%^)...
"%ADB%" logcat -d -b all -v threadtime > "%FULL%" 2>&1
if errorlevel 1 (
    "%ADB%" logcat -d -v threadtime > "%FULL%" 2>&1
)

echo  Extracting the card / EMV lines...
type "%FULL%" | findstr /i ^
 /c:"FindCardActivity" ^
 /c:"kernelProcessResult" ^
 /c:"apiEmvKernelProcess" ^
 /c:"apiEmvGetKernelProcessResult" ^
 /c:"emvGetKernelProgressResult" ^
 /c:"openFindCardForEMV" ^
 /c:"closeFindCardForEMV" ^
 /c:"EmvProcess" ^
 /c:"EmvCompletion" ^
 /c:"AppSelect" ^
 /c:"SelectProcess" ^
 /c:"CmdSendRecvApdu" ^
 /c:"DAL_" ^
 /c:"Kernel" ^
 /c:"ERR_" ^
 /c:"9F2A" ^
 /c:"AID=" ^
 /c:"PPSE" ^
 /c:"2PAY.SYS" ^
 /c:"CAPK" ^
 /c:"cardType" ^
 /c:"OnlineHost" ^
 /c:"PayActivity" ^
 /c:"AF_CORE" ^
 | findstr /v /i ^
 /c:"BufferQueueProducer" ^
 /c:"BufferQueueDebug" ^
 /c:"BufferStateLayer" ^
 /c:"SurfaceFlinger" ^
 /c:"hwcomposer" ^
 /c:"libPerfCtl" ^
 /c:"findCardAsyncStat" ^
 > "%EMV%"

echo.
echo  ============================================================
for %%F in ("%FULL%") do echo   Full log     : %%~nxF   ^(%%~zF bytes^)
for %%F in ("%EMV%")  do echo   EMV subset   : %%~nxF   ^(%%~zF bytes^)
echo   Folder       : %OUTDIR%
echo  ============================================================
echo.

REM -- Did a card transaction actually happen in this window? -------------
findstr /i /c:"kernelProcessResult" /c:"openFindCardForEMV: arming" "%FULL%" >nul 2>&1
if errorlevel 1 (
    echo   [!] WARNING - no card transaction was recorded.
    echo.
    echo       The log only contains the terminal sitting idle. That
    echo       usually means a key was pressed here before the card was
    echo       tapped, or the transaction was run before this script
    echo       cleared the log.
    echo.
    echo       Run this again and tap the card AFTER the recording
    echo       message appears, then come back.
    echo.
) else (
    echo   [OK] A card transaction was recorded. Send the two files on.
    echo.
)

start "" "%OUTDIR%"
pause
