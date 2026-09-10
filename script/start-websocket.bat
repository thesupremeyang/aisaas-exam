@echo off
cd /d "%~dp0.."
mvn spring-boot:run -pl runner-websocket -am
