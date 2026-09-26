@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -version
if errorlevel 1 goto fail
set "APP_JAR=dist\course-selection-platform.jar"
if exist "%APP_JAR%" goto run
set "APP_JAR=target\parallel-volunteer-admission-system-2.0.0.jar"
if exist "%APP_JAR%" goto run
call mvnw.cmd -B package
if errorlevel 1 goto fail
:run
if not defined PORT set PORT=8088
echo Open http://127.0.0.1:%PORT% - admin password: data\admin-credentials.txt
"%JAVA_CMD%" -Duser.timezone=Asia/Shanghai -jar "%APP_JAR%"
if errorlevel 1 goto fail
exit /b 0
:fail
echo Startup failed. Java 17 is required. Check JAVA_HOME and the error above.
pause
exit /b 1
