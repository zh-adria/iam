# ponytail: only vite dev server. Assumes backend already up.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend')
npm install --silent
npm run dev
