@echo off
REM ---------------------------------------------------------------------------
REM  Swap the Anfu SDK interface AAR for the one the Anfu team supplied.
REM
REM  common.gradle pulls in EVERY *.aar in app\libs:
REM
REM      implementation fileTree(include: ['*.jar', '*.aar'], dir: 'libs')
REM
REM  so the old AAR has to LEAVE that folder, not just sit beside the new one -
REM  two copies of com.szanfu.sdk.api.* on the classpath is a duplicate-class
REM  build failure, and if it did build you would not know which one won.
REM
REM  Old AAR is moved to _old_sdk_backup\ rather than deleted.
REM  New AAR is taken from the PARENT folder (where you saved it).
REM ---------------------------------------------------------------------------
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist "app\libs" (
  echo ERROR: no app\libs here. Run this from the project root.
  pause & exit /b 1
)

set "NEWAAR="
for %%F in ("..\AFSDKInterface_*.aar") do set "NEWAAR=%%~fF"
if not defined NEWAAR (
  echo ERROR: no AFSDKInterface_*.aar found in the parent folder:
  echo        %~dp0..
  pause & exit /b 1
)
echo New SDK : !NEWAAR!

if not exist "_old_sdk_backup" mkdir "_old_sdk_backup"

for %%F in ("app\libs\AFSDKInterface_*.aar") do (
  echo Moving out: %%~nxF
  move /y "%%~fF" "_old_sdk_backup\" >nul
)

copy /y "!NEWAAR!" "app\libs\" >nul
if errorlevel 1 (
  echo ERROR: could not copy the new AAR into app\libs
  pause & exit /b 1
)

echo.
echo app\libs now contains:
dir /b "app\libs\*.aar"
echo.
echo Old AAR(s) kept in _old_sdk_backup\ - delete when you are happy.
echo.
echo NEXT: in Android Studio do File - Sync Project with Gradle Files,
echo       then Build - Clean Project, then rebuild.
echo.
pause
