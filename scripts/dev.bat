@echo off
rem IAM full-stack dev starter (Windows). Double-click or run from cmd.
rem Auto-detects JDK 17+ and Maven from PATH / registry / common locations.
rem Bypasses PowerShell execution policy for this run only.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1"
if errorlevel 1 pause
