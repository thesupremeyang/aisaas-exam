@echo off
cd /d "%~dp0.."
title apboa-console
mvn spring-boot:run -pl runner-console -am
