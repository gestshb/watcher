@echo off
setlocal

:: Watcher Start Script for Windows
set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%syshealthd.jar

if not exist "%JAR_PATH%" (
    if exist "%SCRIPT_DIR%..\target\syshealthd.jar" (
        set JAR_PATH=%SCRIPT_DIR%..\target\syshealthd.jar
    ) else if exist "C:\ProgramData\SystemHealth\syshealthd.jar" (
        set JAR_PATH=C:\ProgramData\SystemHealth\syshealthd.jar
    ) else (
        echo Error: syshealthd.jar not found.
        echo Please place syshealthd.jar in the same directory as this script: %SCRIPT_DIR%
        pause
        exit /b 1
    )
)

set JAVA_CMD=
where javaw >nul 2>&1
if %errorlevel% equ 0 (
    set JAVA_CMD=javaw
) else (
    where java >nul 2>&1
    if %errorlevel% equ 0 (
        set JAVA_CMD=java
    ) else (
        echo Error: Java 17+ is required but neither javaw nor java was found in PATH.
        pause
        exit /b 1
    )
)

for /f "tokens=*" %%a in ('powershell -NoProfile -Command "(Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' } | Measure-Object).Count"') do set PROC_COUNT=%%a
if not "%PROC_COUNT%"=="0" (
    echo ==========================================
    echo Watcher is already RUNNING.
    echo ==========================================
    exit /b 0
)

echo Starting Watcher manually in background...
echo JAR: %JAR_PATH%
for %%F in ("%JAR_PATH%") do cd /d "%%~dpF"
start "" "%JAVA_CMD%" -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true -jar "%JAR_PATH%"

timeout /t 2 /nobreak >nul
powershell -NoProfile -Command "$procs = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' }; if ($procs) { Write-Host '=========================================='; Write-Host ' Watcher started successfully.'; Write-Host '=========================================='; $procs | Format-Table ProcessId, Name -AutoSize; exit 0 } else { Write-Host 'Error: Watcher failed to start.'; exit 1 }"
