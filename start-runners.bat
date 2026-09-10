@echo off
setlocal
set "JAVA_HOME=C:\JAVA\Java Tool\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ROOT=C:\JAVA\apboa_saas"

echo Starting runner-console (port 3060)...
start "runner-console" cmd /c "cd /d %ROOT% && mvn spring-boot:run -pl runner-console > logs\console.log 2>&1"

echo Starting runner-runtime (port 3061)...
start "runner-runtime" cmd /c "cd /d %ROOT% && mvn spring-boot:run -pl runner-runtime > logs\runtime.log 2>&1"

echo Starting runner-proxy (port 3062)...
start "runner-proxy" cmd /c "cd /d %ROOT% && mvn spring-boot:run -pl runner-proxy > logs\proxy.log 2>&1"

echo Starting runner-file (port 3063)...
start "runner-file" cmd /c "cd /d %ROOT% && mvn spring-boot:run -pl runner-file > logs\file.log 2>&1"

echo Starting runner-websocket (port 3064)...
start "runner-websocket" cmd /c "cd /d %ROOT% && mvn spring-boot:run -pl runner-websocket > logs\websocket.log 2>&1"

echo.
echo All backend services starting...
