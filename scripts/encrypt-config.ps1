# Encrypt database configuration values for IAM.
#
# Usage:
#   .\scripts\encrypt-config.ps1 -ConfigKey "your-secret-key" -Values "url","user","pass"
#
# Pass datasource values explicitly; this script does not embed environment secrets.
#
# Prerequisites: JDK 17+, Maven (same as dev.ps1 requirements)

param(
    [Parameter(Mandatory=$true)]
    [string]$ConfigKey,

    [string[]]$Values
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

Write-Host ""
Write-Host "=== IAM Config Encryption Tool ==="
Write-Host ""

# --- auto-detect JDK (same as dev.ps1) ---
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
  }
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
if (-not $javaHome -or -not (Test-Path "$javaHome\bin\java.exe")) {
  Write-Host "ERROR: JDK 17+ not found."
  exit 1
}
$env:JAVA_HOME = $javaHome

# --- find Maven ---
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
  Write-Host "ERROR: Maven not found on PATH."
  exit 1
}

# --- compile iam-common ---
Write-Host "[1/3] compiling iam-common..."
Push-Location backend
mvn -B -q -pl iam-common -am compile
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: compile failed"; Pop-Location; exit 1 }
Pop-Location

# --- build classpath ---
Write-Host "[2/3] building classpath..."
Push-Location backend
try {
    $cp = mvn -B -q -pl iam-common 'dependency:build-classpath' '-Dmdep.outputFile=/dev/stdout'
} finally {
    Pop-Location
}
$cp = "$cp;backend\iam-common\target\classes"

# --- encrypt ---
Write-Host "[3/3] encrypting values..."
Write-Host ""

$toolClass = "com.iam.infrastructure.config.ConfigEncryptTool"

if (-not $Values -or $Values.Count -eq 0) {
    Write-Host "ERROR: -Values is required. Pass url, username, and password explicitly."
    exit 1
}

$labels = @("url", "username", "password")

for ($i = 0; $i -lt $Values.Count; $i++) {
    $label = $labels[$i]
    $val = $Values[$i]
    Write-Host "  [$label]"
    Write-Host "    plaintext: $val"
    $encrypted = & "$javaHome\bin\java.exe" -cp $cp $toolClass $ConfigKey $val 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR encrypting $label : $encrypted"
        exit 1
    }
    Write-Host "    encrypted: $encrypted"
    Write-Host ""
}

Write-Host "=== Done ==="
Write-Host ""
Write-Host "Copy the encrypted values above into your application-dev.yml / application.yml"
Write-Host "Make sure IAM_CONFIG_KEY environment variable is set when starting the app."
