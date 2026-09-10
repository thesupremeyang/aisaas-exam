$ErrorActionPreference = "Continue"

Write-Host "正在关闭 APBOA-Next 相关进程..."

# 关闭 java (mvn/spring-boot)、node (pnpm dev)
Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object {
  Write-Host ("  kill java pid=" + $_.Id)
  Stop-Process -Id $_.Id -Force
}
Get-Process -Name node -ErrorAction SilentlyContinue | ForEach-Object {
  $cmdLine = (Get-CimInstance Win32_Process -Filter ("ProcessId=" + $_.Id) -ErrorAction SilentlyContinue).CommandLine
  if ($cmdLine -and $cmdLine -match "pnpm|vite") {
    Write-Host ("  kill pnpm/vite pid=" + $_.Id)
    Stop-Process -Id $_.Id -Force
  }
}
Start-Sleep -Seconds 2
Write-Host "已关闭。"
