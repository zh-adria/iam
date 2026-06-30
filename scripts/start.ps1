# ponytail: docker compose full stack. PowerShell counterpart of start.sh.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

docker compose up -d --build

Write-Host ""
Write-Host "IAM 平台已启动 (docker):"
Write-Host "  Auth Server: http://localhost:8080/iam"
Write-Host "  Admin Server: http://localhost:8081/iam"
Write-Host "  MySQL:       localhost:3306  (iam / iam123)"
Write-Host "  Redis:       localhost:6379"
Write-Host "  演示账号:    admin / Iam@2026,  alice / User@2026"
Write-Host "  OAuth2:      demo-client / demo-secret"
Write-Host ""
Write-Host "  日志: docker compose logs -f iam-auth-server"
Write-Host "  停止: .\scripts\stop.ps1"
