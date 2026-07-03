# IAM full-stack dev starter (Windows PowerShell).
# Run via:  powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
# or after `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once:  .\scripts\dev.ps1
#
# Auto-detects JDK 17 and Maven from PATH; falls back to sensible Windows defaults.
# Redis provisioning order:
#   1) docker compose (if docker available & running)
#   2) local redis on localhost:6379 (if redis-cli ping works)
#   3) auto-download portable Windows Redis into scripts\redis\
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

# --- auto-detect JDK 17+ ---
function Find-JavaHome {
  # honor env override
  if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return $env:JAVA_HOME }
  # try registry
  foreach ($regPath in @('HKLM:\SOFTWARE\JavaSoft\JDK','HKLM:\SOFTWARE\JavaSoft\Java Development Kit')) {
    try {
      $ver = (Get-ItemProperty $regPath -ErrorAction SilentlyContinue).CurrentVersion
      if ($ver) {
        $home = (Get-ItemProperty "$regPath\$ver" -ErrorAction SilentlyContinue).JavaHome
        if ($home -and (Test-Path "$home\bin\java.exe")) { return $home }
      }
    } catch {}
  }
  # fallback: search common locations
  foreach ($p in @('C:\Program Files\Java','C:\Program Files (x86)\Java','D:\Program Files\Java')) {
    if (Test-Path $p) {
      $dirs = Get-ChildItem $p -Directory | Where-Object { $_.Name -match '^jdk-?(17|21|22)' } |
              Sort-Object Name -Descending | Select-Object -First 1
      if ($dirs -and (Test-Path "$($dirs.FullName)\bin\java.exe")) { return $dirs.FullName }
    }
  }
  # last resort: java on PATH, derive JAVA_HOME from it
  try {
    $jPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($jPath) {
      $resolved = (Get-Item $jPath -ErrorAction SilentlyContinue).Target
      if ($resolved -and (Test-Path $resolved)) {
        $jDir = Split-Path (Split-Path $resolved -Parent) -Parent
        if ($jDir -and (Test-Path "$jDir\bin\javac.exe")) { return $jDir }
      }
    }
  } catch {}
  return $null
}
$javaHome = Find-JavaHome
if (-not $javaHome -or -not (Test-Path "$javaHome\bin\javac.exe")) {
  Write-Host "ERROR: JDK 17+ not found. Install JDK 17/21/22 or set JAVA_HOME."
  Write-Host "Searched: C:\Program Files\Java, registry, PATH"
  exit 1
}
$env:JAVA_HOME = $javaHome
Write-Host "      using JDK at $javaHome"

# --- auto-detect Maven ---
function Find-MavenDir {
  if ($env:M2_HOME -and (Test-Path "$env:M2_HOME\bin\mvn.cmd")) { return $env:M2_HOME }
  try {
    $mvnPath = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if ($mvnPath) { return Split-Path (Split-Path $mvnPath -Parent) -Parent }
  } catch {}
  foreach ($p in @('C:\Program Files\apache-maven','C:\Program Files (x86)\apache-maven',
                    'D:\Program Files\apache-maven','D:\Program Files (x86)\apache-maven')) {
    if (Test-Path $p) {
      $dirs = (Get-ChildItem $p -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
      if ($dirs) { return $dirs[0].FullName }
    }
  }
  return $null
}
$mavenHome = Find-MavenDir
if (-not $mavenHome -or -not (Test-Path "$mavenHome\bin\mvn.cmd")) {
  Write-Host "ERROR: Maven not found. Install Maven or set M2_HOME."
  exit 1
}
$env:M2_HOME = $mavenHome
$env:PATH = "$env:JAVA_HOME\bin;$env:M2_HOME\bin;$env:PATH"
Write-Host "      using Maven at $mavenHome"

# user manages their own Redis — script doesn't start/stop it

function Wait-Until($fn, $seconds) {
  for ($i=1; $i -le $seconds; $i++) {
    if (& $fn) { return $true }
    Start-Sleep -Seconds 1
  }
  return $false
}

function Test-RedisUp {
  try {
    $cli = Join-Path $root 'scripts\redis\redis-cli.exe'
    if (Test-Path $cli) {
      $out = & $cli -h localhost -p 6379 ping 2>$null
      if ($out -match 'PONG') { return $true }
    }
    $tcp = New-Object Net.Sockets.TcpClient
    $iar = $tcp.BeginConnect('localhost', 6379, $null, $null)
    if ($iar.AsyncWaitHandle.WaitOne(500)) { $tcp.EndConnect($iar); $tcp.Close(); return $true }
  } catch {}
  return $false
}

function Ensure-Redis {
  # just use local redis on localhost:6379 — user manages their own Redis
  if (Test-RedisUp) { Write-Host "      using local redis on localhost:6379"; return }
  throw "Redis not running on localhost:6379. Start your local Redis first."
}

Write-Host "[1/4] redis check"
Ensure-Redis

Write-Host "[2/4] building backend (skip tests)"
Push-Location backend
mvn -B -q -DskipTests package
Pop-Location

New-Item -ItemType Directory -Force -Path logs | Out-Null

Write-Host "[3/4] starting iam-auth-server (8080) - dev profile"
$auth = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
  -ArgumentList '-jar','backend\iam-auth-server\target\iam-auth-server-1.0.0-SNAPSHOT.jar','--spring.profiles.active=dev' `
  -RedirectStandardOutput 'logs\auth-server.log' `
  -RedirectStandardError  'logs\auth-server.err.log' `
  -PassThru -WindowStyle Hidden
Write-Host "  auth-server pid=$($auth.Id)  log=logs\auth-server.log"

Write-Host "      waiting for auth-server health..."
if (-not (Wait-Until {
  try { (Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/iam/actuator/health' -TimeoutSec 2).StatusCode -eq 200 }
  catch { $false }
} 90)) {
  Write-Host "ERROR: auth-server didn't become healthy in 90s. Check logs\auth-server.log"
}

Write-Host "      starting iam-admin (8081)"
$admin = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
  -ArgumentList '-jar','backend\iam-admin\target\iam-admin-1.0.0-SNAPSHOT.jar','--spring.profiles.active=dev' `
  -RedirectStandardOutput 'logs\admin.log' `
  -RedirectStandardError  'logs\admin.err.log' `
  -PassThru -WindowStyle Hidden
Write-Host "  admin pid=$($admin.Id)  log=logs\admin.log"

Write-Host "[4/4] starting frontend dev server (5173)"
Push-Location frontend
npm install --silent
$front = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev' -PassThru -NoNewWindow
Pop-Location

Write-Host ""
Write-Host "IAM 平台已启动 (dev profile, RS256 JWK, H2):"
Write-Host "  前端:         http://localhost:5173"
Write-Host "  Auth Server:  http://localhost:8080/iam"
Write-Host "  Admin Server: http://localhost:8081/iam"
Write-Host "  H2 JDBC URL:  jdbc:h2:file:$env:USERPROFILE\iam-dev\iam;AUTO_SERVER=TRUE;MODE=MySQL"
Write-Host "  Redis:        localhost:6379"
Write-Host "  演示账号:     admin / Iam@2026,  alice / User@2026"
Write-Host "  OAuth2:       demo-client / demo-secret"
Write-Host "  SAML:         admin 后台动态注册,  POST /admin/api/saml/idps"
Write-Host "  Ctrl-C 停止所有服务"
Write-Host ""

try {
  while (-not $front.HasExited) { Start-Sleep -Seconds 1 }
} finally {
  Write-Host "`nshutting down..."
  foreach ($p in @($front, $auth, $admin)) {
    if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
  }
  Write-Host "  (local Redis left running)"
}
