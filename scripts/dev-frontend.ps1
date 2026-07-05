# IAM — 仅启动前端（Vite dev server）
# 用法: .\scripts\dev-frontend.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "starting frontend dev server (5173)..."
Push-Location frontend
npm install --silent 2>$null
npm run dev
Pop-Location
