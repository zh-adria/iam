# ponytail: clean build backend + frontend. No run.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

# ponytail: force JDK 17 — user's system JAVA_HOME may point to JDK 8.
$env:JAVA_HOME = 'D:\Program Files (x86)\jdk17'
$env:PATH = "$env:JAVA_HOME\bin;D:\Program Files (x86)\apache-maven-3.9.7\bin;$env:PATH"

Write-Host "=== backend: mvn clean package (with tests) ==="
Push-Location backend
mvn -B clean package
Pop-Location

Write-Host "=== frontend: npm build ==="
Push-Location frontend
npm install --silent
npm run build
Pop-Location

Write-Host ""
Write-Host "build OK:"
Write-Host "  backend\iam-auth-server\target\iam-auth-server-1.0.0-SNAPSHOT.jar"
Write-Host "  backend\iam-admin\target\iam-admin-1.0.0-SNAPSHOT.jar"
Write-Host "  frontend\dist\"
