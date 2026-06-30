@echo off
rem ponytail: thin wrapper around dev.ps1. Double-click or run from cmd.
rem Bypasses PowerShell execution policy for this run only.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1"
if errorlevel 1 pause
