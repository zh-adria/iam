@echo off
rem IAM one-click launcher (Windows). Double-click or run from cmd.
rem Directly starts the dev stack via dev.ps1 — no menu, no interaction needed.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1"
if errorlevel 1 pause
