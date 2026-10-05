@echo off
title ANFU AF930 - live EMV log

REM ---------------------------------------------------------------------------
REM  Watch the card / EMV lines scroll past in real time. Nothing is saved.
REM  Use collect_log.bat when you need a file to send to someone.
REM  Close the window or press Ctrl+C to stop.
REM ---------------------------------------------------------------------------

set "ADB=D:\Development_environment_resource_package\Development_environment_resource_package\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo   [X] adb.exe not found at %ADB%
    pause
    exit /b 1
)

"%ADB%" logcat -c >nul 2>&1
echo.
echo   Watching. Tap the card now. Ctrl+C to stop.
echo.
"%ADB%" logcat -v time | findstr /i ^
 /c:"FindCardActivity" ^
 /c:"kernelProcessResult" ^
 /c:"apiEmvKernelProcess" ^
 /c:"openFindCardForEMV" ^
 /c:"EmvProcess" ^
 /c:"AppSelect" ^
 /c:"CmdSendRecvApdu" ^
 /c:"Kernel" ^
 /c:"ERR_" ^
 /c:"9F2A" ^
 /c:"AID=" ^
 /c:"cardType"
pause
