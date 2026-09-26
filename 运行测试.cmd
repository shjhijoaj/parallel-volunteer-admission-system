@echo off
chcp 65001 >nul
cd /d "%~dp0"
call mvnw.cmd -B test
pause
