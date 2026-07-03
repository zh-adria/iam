# Backend-only dev starter (Windows). Auto-detects JDK 17+ and Maven.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

function Find-JavaHome {
  if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return $env:JAVA_HOME }
  foreach (@('HKLM:\SOFTWARE\JavaSoft\JDK','HKLM:\SOFTWARE\JavaSoft\Java Development Kit')) {
    try {
      $ver = (Get-ItemProperty $_ -ErrorAction SilentlyContinue).CurrentVersion
      if ($ver) {
        $home = (Get-ItemProperty "$_\$ver" -ErrorAction SilentlyContinue).JavaHome
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
  }
  return $null
}
function Find-MavenDir {
  if ($env:M2_HOME -and (Test-Path "$env:M2_HOME\bin\mvn.cmd")) { return $env:M2_HOME }
  try {
    $mvnPath = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if ($mvnPath) { return Split-Path (Split-Path $mvnPath -Parent) -Parent }
  } catch {}
  foreach ($p in @('C:\Program Files\apache-maven','C:\Program Files (x86)\apache-maven')) {
    if (Test-Path $p) {
      $dirs = (Get-ChildItem $p -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
      if ($dirs) { return $dirs[0].FullName }
    }
  }
  return $null
}
$javaHome = Find-JavaHome
if (-not $javaHome -or -not (Test-Path "$javaHome\bin\javac.exe")) {
  Write-Host "ERROR: JDK 17+ not found. Install or set JAVA_HOME."; exit 1
}
$env:JAVA_HOME = $javaHome
$mavenHome = Find-MavenDir
if (-not $mavenHome -or -not (Test-Path "$mavenHome\bin\mvn.cmd")) {
  Write-Host "ERROR: Maven not found. Install or set M2_HOME."; exit 1
}
$env:M2_HOME = $mavenHome
$env:PATH = "$env:JAVA_HOME\bin;$env:M2_HOME\bin;$env:PATH"

$script:redisProc = $null
$script:redisFromDocker = $false

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
  $dockerOk = $false
  try { if (Get-Command docker -ErrorAction SilentlyContinue) { docker info 2>$null | Out-Null; $dockerOk = $LASTEXITCODE -eq 0 } } catch {}
  if ($dockerOk) {
    docker compose up -d redis
    if (Wait-Until { (docker exec iam-redis redis-cli ping 2>$null) -match 'PONG' } 15) {
      $script:redisFromDocker = $true; return
    }
  }
  if (Test-RedisUp) { Write-Host "  using local redis on localhost:6379"; return }
  $redisDir = Join-Path $root 'scripts\redis'
  $redisExe = Join-Path $redisDir 'redis-server.exe'
  if (-not (Test-Path $redisExe)) {
    Write-Host "  downloading portable Windows Redis (one-time, ~12MB)"
    New-Item -ItemType Directory -Force -Path $redisDir | Out-Null
    $zip = Join-Path $redisDir 'redis.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $redisDir -Force
    Remove-Item $zip -Force
    if (-not (Test-Path $redisExe)) {
      $found = Get-ChildItem -Path $redisDir -Recurse -Filter 'redis-server.exe' | Select-Object -First 1
      if ($found) { Move-Item $found.FullName (Join-Path $redisDir 'redis-server.exe') -Force }
    }
  }
  $dataDir = Join-Path $redisDir 'data'
  New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
  $script:redisProc = Start-Process -FilePath $redisExe `
    -ArgumentList '--port','6379','--dir',$dataDir `
    -RedirectStandardOutput 'logs\redis.log' `
    -RedirectStandardError  'logs\redis.err.log' `
    -WindowStyle Hidden -PassThru
  Start-Sleep -Seconds 1
  if ($script:redisProc.HasExited) { throw "redis-server exited immediately, see logs\redis.err.log" }
  if (-not (Wait-Until { Test-RedisUp } 15)) { throw "redis didn't come up on 6379" }
}

Write-Host "      using JDK at $javaHome"
Write-Host "      using Maven at $mavenHome"
Write-Host "redis check..."
Ensure-Redis

Write-Host "building backend (skip tests)..."
Push-Location backend
mvn -B -q -DskipTests package
Pop-Location

New-Item -ItemType Directory -Force -Path logs | Out-Null

Write-Host "starting iam-auth-server (8080) - dev profile (RS256)"
$auth = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
  -ArgumentList '-jar','backend\iam-auth-server\target\iam-auth-server-1.0.0-SNAPSHOT.jar','--spring.profiles.active=dev' `
  -RedirectStandardOutput 'logs\auth-server.log' `
  -RedirectStandardError  'logs\auth-server.err.log' `
  -PassThru -WindowStyle Hidden
Write-Host "  auth-server pid=$($auth.Id)  log=logs\auth-server.log"

Write-Host "waiting for auth-server health..."
# ponytail: check StatusCode==200, not .Content -match 'UP'. PS 5.x returns
# .Content as a byte array for application/vnd.spring-boot.actuator.v3+json.
if (-not (Wait-Until {
  try { (Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/iam/actuator/health' -TimeoutSec 2).StatusCode -eq 200 }
  catch { $false }
} 90)) {
  Write-Host "ERROR: auth-server didn't become healthy. Check logs\auth-server.log"
}

# foreground admin — Ctrl-C exits (also kills background via finally)
$admin = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
  -ArgumentList '-jar','backend\iam-admin\target\iam-admin-1.0.0-SNAPSHOT.jar','--spring.profiles.active=dev' `
  -PassThru -NoNewWindow

try {
  $admin.WaitForExit()
} finally {
  Write-Host "`nstopping..."
  foreach ($p in @($auth, $admin)) {
    if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
  }
  if ($script:redisProc -and -not $script:redisProc.HasExited) {
    Stop-Process -Id $script:redisProc.Id -Force -ErrorAction SilentlyContinue
  }
  if ($script:redisFromDocker) { docker compose stop redis 2>$null }
}
