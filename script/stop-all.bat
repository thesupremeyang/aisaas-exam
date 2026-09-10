@echo off
REM 关闭所有 APBOA-Next 相关 Java 进程及前端进程
echo 正在关闭 APBOA-Next 相关进程...

taskkill /FI "WINDOWTITLE eq apboa-console*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq apboa-file*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq apboa-runtime*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq apboa-websocket*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq apboa-proxy*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq apboa-ui*" /T /F >nul 2>&1

echo 已关闭。
timeout /t 2 /nobreak >nul
