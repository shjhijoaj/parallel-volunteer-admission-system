@echo off
chcp 65001 > nul
setlocal
cd /d "%~dp0"
echo ==============================================
echo   平行志愿录取系统 - 本地启动
echo ==============================================
echo.
echo 启动前请先设置数据库密码，例如：
echo     set MYSQL_PASSWORD=你的数据库密码
echo     set SQL_INIT_MODE=always   ^(首次建表时使用^)
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\run-dev.ps1" %*
if errorlevel 1 pause
endlocal
