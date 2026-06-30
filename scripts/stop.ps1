# ponytail: stop everything dev.ps1 or start.ps1 may have started.
$ErrorActionPreference = 'SilentlyContinue'

# kill any locally-run backend jars + vite (match by command line)
Get-CimInstance Win32_Process | Where-Object {
  $_.CommandLine -like '*iam-auth-server-*.jar*' -or
  $_.CommandLine -like '*iam-admin-*.jar*' -or
  $_.CommandLine -like '*vite*'
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

# stop docker-compose stack
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose stop

Write-Host "stopped: backend jars, vite, docker compose services"
