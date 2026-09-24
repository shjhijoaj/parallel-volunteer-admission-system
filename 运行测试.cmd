@echo off
chcp 65001 > nul
setlocal
cd /d "%~dp0"
echo ==============================================
echo   平行志愿录取系统 - 自动化测试
echo ==============================================
echo.
echo 未设置 MYSQL_PASSWORD 时只运行规则单元测试。
echo 设置后会额外运行 MySQL 端到端集成测试（需要独立测试库）。
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\verify.ps1" %*
pause
endlocal
