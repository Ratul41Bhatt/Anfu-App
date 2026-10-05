@echo off
cd /d "%~dp0"
python fix_de22.py
if errorlevel 1 (
  py fix_de22.py
)
echo.
pause
