@echo off
setlocal
set JAVA_HOME=C:\JAVA\JAVATO~1\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d C:\JAVA\apboa_saas

echo Starting all runners...

start "console" cmd /c "set JAVA_HOME=C:\JAVA\JAVATO~1\jdk-21 && set PATH=%JAVA_HOME%\bin;%PATH% && cd /d C:\JAVA\apboa_saas && mvn spring-boot:run -pl runner-console > logs\console.log 2>&1"
start "runtime" cmd /c "set JAVA_HOME=C:\JAVA\JAVATO~1\jdk-21 && set PATH=%JAVA_HOME%\bin;%PATH% && cd /d C:\JAVA\apboa_saas && mvn spring-boot:run -pl runner-runtime > logs\runtime.log 2>&1"
start "proxy" cmd /c "set JAVA_HOME=C:\JAVA\JAVATO~1\jdk-21 && set PATH=%JAVA_HOME%\bin;%PATH% && cd /d C:\JAVA\apboa_saas && mvn spring-boot:run -pl runner-proxy > logs\proxy.log 2>&1"
start "file" cmd /c "set JAVA_HOME=C:\JAVA\JAVATO~1\jdk-21 && set PATH=%JAVA_HOME%\bin;%PATH% && cd /d C:\JAVA\apboa_saas && mvn spring-boot:run -pl runner-file > logs\file.log 2>&1"
start "websocket" cmd /c "set JAVA_HOME=C:\JAVA\JAVATO~1\jdk-21 && set PATH=%JAVA_HOME%\bin;%PATH% && cd /d C:\JAVA\apboa_saas && mvn spring-boot:run -pl runner-websocket > logs\websocket.log 2>&1"

echo All runners started. Check logs folder for details.
