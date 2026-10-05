@echo off
setlocal

set PASSWORD=%~1
if "%PASSWORD%"=="" set PASSWORD=SecretParent123
set PORT=%~2
if "%PORT%"=="" set PORT=8080

set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%syshealthd.jar
if not exist "%JAR_PATH%" set JAR_PATH=%SCRIPT_DIR%..\target\syshealthd.jar
if not exist "%JAR_PATH%" set JAR_PATH=C:\ProgramData\SystemHealth\syshealthd.jar

if not exist "%JAR_PATH%" (
    echo Error: syshealthd.jar not found.
    pause
    exit /b 1
)

for %%F in ("%JAR_PATH%") do cd /d "%%~dpF"
java -jar "%JAR_PATH%" -p "%PASSWORD%" --viewer --viewer-port %PORT%
pause
