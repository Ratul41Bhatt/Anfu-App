@echo off
title Anfu AF980 Card Reader Log Collector
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\collect_card_reader_log.ps1" %*
pause
