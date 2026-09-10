$ErrorActionPreference = "Continue"
Set-Location "c:\Users\Administrator\Desktop\分析系统\apboa-next-master"

$logDir = ".\.tmp_logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

$sep = "-" * 60

Write-Host ""
Write-Host $sep
Write-Host "  APBOA-Next Build + Start"
Write-Host $sep

Write-Host ""
Write-Host "[1/3] mvn install (first time ~5-15 min) ..."
Write-Host ("       log: " + $logDir + "\mvn-install.log")
Write-Host ""

$installLog = Join-Path $logDir "mvn-install.log"
$proc = Start-Process -FilePath "mvn.cmd" `
  -ArgumentList "clean","install","-DskipTests","-pl","runner-console,runner-file,runner-runtime,runner-websocket,runner-proxy","-am" `
  -WorkingDirectory (Resolve-Path "c:\Users\Administrator\Desktop\分析系统\apboa-next-master") `
  -RedirectStandardOutput $installLog `
  -RedirectStandardError ($installLog + ".err") `
  -NoNewWindow -PassThru -Wait

if ($proc.ExitCode -ne 0) {
  Write-Host ("[FAIL] mvn install failed, see " + $installLog)
  exit 1
}
Write-Host "[OK] mvn install done"

$root = (Resolve-Path "c:\Users\Administrator\Desktop\分析系统\apboa-next-master").Path

Write-Host ""
Write-Host "[2/3] Starting 5 backend services ..."
Write-Host ("       log: " + $logDir + "\runner-*.log")
Write-Host ""

$runners = @(
  @{ Name = "console";    Module = "runner-console";   Port = 3060 },
  @{ Name = "file";       Module = "runner-file";      Port = 3063 },
  @{ Name = "runtime";    Module = "runner-runtime";   Port = 3061 },
  @{ Name = "websocket";  Module = "runner-websocket"; Port = 3064 },
  @{ Name = "proxy";      Module = "runner-proxy";     Port = 3062 }
)

foreach ($r in $runners) {
  $log = Join-Path $logDir ("runner-" + $r.Name + ".log")
  Write-Host ("  - starting " + $r.Name + " (port " + $r.Port + ")")
  Start-Process -FilePath "mvn.cmd" `
    -ArgumentList "spring-boot:run","-pl",$r.Module `
    -WorkingDirectory $root `
    -RedirectStandardOutput $log `
    -RedirectStandardError ($log + ".err") `
    -WindowStyle Hidden
  Start-Sleep -Seconds 5
}

Write-Host ""
Write-Host "       waiting 40s for backends to start..."
Start-Sleep -Seconds 40

$uiDir = Join-Path $root "ui"
$uiLog = Join-Path $logDir "ui.log"
Write-Host ""
Write-Host "[3/3] Starting frontend (port 5173) ..."
Write-Host ("       log: " + $uiLog)
Write-Host ""

Start-Process -FilePath "pnpm.cmd" `
  -ArgumentList "dev" `
  -WorkingDirectory $uiDir `
  -RedirectStandardOutput $uiLog `
  -RedirectStandardError ($uiLog + ".err") `
  -WindowStyle Hidden

Write-Host ""
Write-Host $sep
Write-Host "  All services started"
Write-Host "  - console     http://localhost:3060"
Write-Host "  - runtime     http://localhost:3061"
Write-Host "  - file        http://localhost:3063 (heartbeat)"
Write-Host "  - websocket   http://localhost:3064"
Write-Host "  - proxy       http://localhost:3062 (shell)"
Write-Host "  - frontend    http://localhost:5173"
Write-Host ""
Write-Host ("  Log dir: " + $logDir)
Write-Host $sep
