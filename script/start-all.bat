@echo off
REM ============================================================
REM APBOA-Next 一键启动脚本（Windows）
REM 启动顺序：console -> file -> runtime -> websocket -> proxy -> ui
REM ============================================================

setlocal
set "ROOT=%~dp0.."
cd /d "%ROOT%"

echo.
echo ============================================================
echo   1/5  启动 console  (端口 3060)
echo ============================================================
start "apboa-console" cmd /k "mvn spring-boot:run -pl runner-console -am"

timeout /t 30 /nobreak >nul

echo.
echo ============================================================
echo   2/5  启动 file     (心跳)
echo ============================================================
start "apboa-file" cmd /k "mvn spring-boot:run -pl runner-file -am"

timeout /t 10 /nobreak >nul

echo.
echo ============================================================
echo   3/5  启动 runtime  (端口 3061)
echo ============================================================
start "apboa-runtime" cmd /k "mvn spring-boot:run -pl runner-runtime -am"

timeout /t 30 /nobreak >nul

echo.
echo ============================================================
echo   4/5  启动 websocket (端口 3064)
echo ============================================================
start "apboa-websocket" cmd /k "mvn spring-boot:run -pl runner-websocket -am"

timeout /t 10 /nobreak >nul

echo.
echo ============================================================
echo   5/5  启动 proxy    (shell 代理)
echo ============================================================
start "apboa-proxy" cmd /k "mvn spring-boot:run -pl runner-proxy -am"

timeout /t 10 /nobreak >nul

echo.
echo ============================================================
echo   启动前端 (端口 5173)
echo ============================================================
cd /d "%ROOT%\ui"
start "apboa-ui" cmd /k "pnpm dev:main"

echo.
echo ============================================================
echo   全部服务已启动
echo   - console     http://localhost:3060
echo   - runtime     http://localhost:3061
echo   - file        (心跳服务)
echo   - websocket   http://localhost:3064
echo   - proxy       (shell 代理)
echo   - 前端        http://localhost:5173
echo ============================================================
endlocal
