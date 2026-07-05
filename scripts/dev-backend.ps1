# IAM — 仅启动后端（auth-server + admin）
# 用法: .\scripts\dev-backend.ps1
param(
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

# --- auto-detect JDK & Maven (same as dev.ps1) ---
function Find-JavaHome {
  if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return $env:JAVA_HOME }
  foreach ($regPath in @('HKLM:\SOFTWARE\JavaSoft\JDK','HKLM:\SOFTWARE\JavaSoft\Java Development Kit')) {
    try {
      $ver = (Get-ItemProperty $regPath -ErrorAction SilentlyContinue).CurrentVersion
      if ($ver) {
        $home = (Get-ItemProperty "$regPath\$ver" -ErrorAction SilentlyContinue).JavaHome
        if ($home -and (Test-Path "$home\bin\java.exe")) { return $home }
      }
    } catch {}
  }
  foreach ($p in @('C:\Program Files\Java','C:\Program Files (x86)\Java','D:\Program Files\Java')) {
    if (Test-Path $p) {
      $dirs = Get-ChildItem $p -Directory | Where-Object { $_.Name -match '^jdk-?(17|21|22)' } |
              Sort-Object Name -Descending | Select-Object -First 1
      if ($dirs -and (Test-Path "$($dirs.FullName)\bin\java.exe")) { return $dirs.FullName }
    }
  } try {
    $jPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($jPath) { $r = (Get-Item $jPath -ErrorAction SilentlyContinue).Target
      if ($r -and (Test-Path $r)) { $d = Split-Path (Split-Path $r -Parent) -Parent
        if ($d -and (Test-Path "$d\bin\javac.exe")) { return $d } } }
  } catch {}
  return $null
}
$javaHome = Find-JavaHome
if (-not $javaHome) { Write-Host "ERROR: JDK 17+ not found"; exit 1 }
$env:JAVA_HOME = $javaHome

$mavenHome = if ($env:M2_HOME -and (Test-Path "$env:M2_HOME\bin\mvn.cmd")) { $env:M2_HOME }
  else { $p = (Get-Command mvn -ErrorAction SilentlyContinue).Source; if ($p) { Split-Path (Split-Path $p -Parent) -Parent } }
if (-not $mavenHome) { Write-Host "ERROR: Maven not found"; exit 1 }
$env:M2_HOME = $mavenHome
$env:PATH = "$env:JAVA_HOME\bin;$env:M2_HOME\bin;$env:PATH"

Write-Host "[0/3] stopping any previous java processes"
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host "[1/3] redis check"
function Test-RedisUp {
  try { $out = & (Join-Path $root 'scripts\redis\redis-cli.exe') -h localhost -p 6379 ping 2>$null
    if ($out -match 'PONG') { return $true } }
  catch {}
  try { $tcp = New-Object Net.Sockets.TcpClient; $iar = $tcp.BeginConnect('localhost',6379,$null,$null)
    if ($iar.AsyncWaitHandle.WaitOne(500)) { $tcp.EndConnect($iar); $tcp.Close(); return $true } }
  catch {}
  return $false
}
if (-not (Test-RedisUp)) { throw "Redis not running on localhost:6379. Start Redis first." }
Write-Host "      using local redis on localhost:6379"

Write-Host "[2/3] building backend (skip tests)"
Push-Location backend
mvn -B -q -DskipTests package
Pop-Location
New-Item -ItemType Directory -Force -Path logs | Out-Null

Write-Host "[3/3] starting services"
Write-Host "  starting iam-auth-server (8080) — dev profile"
$auth = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
  -ArgumentList '-jar','backend\iam-auth-server\target\boot\iam-auth-server.jar','--spring.profiles.active=dev' `
  -RedirectStandardOutput 'logs\auth-server.log' -RedirectStandardError 'logs\auth-server.err.log' `
  -PassThru -WindowStyle Hidden
Write-Host "  auth-server pid=$($auth.Id)"

Write-Host "      waiting for auth-server health..."
for ($i=1; $i -le 90; $i++) {
  try { if ((Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/iam/actuator/health' -TimeoutSec 2).StatusCode -eq 200) { break } }
  catch {}
  Start-Sleep -Seconds 1
  if ($i -eq 90) { Write-Host "ERROR: auth-server didn't become healthy in 90s"; exit 1 }
}

Write-Host "  starting iam-admin (8081)"
$admin = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
  -ArgumentList '-jar','backend\iam-admin\target\boot\iam-admin.jar','--spring.profiles.active=dev' `
  -RedirectStandardOutput 'logs\admin.log' -RedirectStandardError 'logs\admin.err.log' `
  -PassThru -WindowStyle Hidden
Write-Host "  admin pid=$($admin.Id)"

Write-Host ""
Write-Host "IAM 后端已启动 (dev profile):"
Write-Host "  Auth Server:  http://localhost:8080/iam"
Write-Host "  Admin Server: http://localhost:8081/iam"
Write-Host "  演示账号:     admin / Iam@2026, alice / User@2026"
Write-Host "  日志:         logs/auth-server.log, logs/admin.log"
Write-Host ""

if (-not $SkipFrontend) {
  Write-Host "  启动前端 dev server (5173)..."
  Push-Location frontend
  npm install --silent 2>$null
  $front = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev' -PassThru -NoNewWindow
  Pop-Location
  Write-Host "  前端: http://localhost:5173"
  Write-Host "  Ctrl-C 停止所有服务"
  Write-Host ""
  try { while (-not $front.HasExited) { Start-Sleep -Seconds 1 } }
  finally {
    Write-Host "`nshutting down..."
    foreach ($p in @($front, $auth, $admin)) {
      if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    }
  }
} else {
  Write-Host "  后端已启动。按任意键停止..."
  $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
  foreach ($p in @($auth, $admin)) {
    if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
  }
}
