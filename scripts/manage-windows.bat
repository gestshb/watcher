@echo off
setlocal enabledelayedexpansion

:: Watcher Management Script for Windows (Start, Status, Stop)
set ACTION=%~1
if "%ACTION%"=="" set ACTION=status

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
        exit /b 1
    )
)

:: Find java executable (prefer javaw for silent execution)
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
        exit /b 1
    )
)

if /i "%ACTION%"=="start" goto do_start
if /i "%ACTION%"=="status" goto do_status
if /i "%ACTION%"=="test" goto do_status
if /i "%ACTION%"=="check" goto do_status
if /i "%ACTION%"=="stop" goto do_stop

echo Usage: %~nx0 {start ^| status ^| stop}
echo   start  : Launch Watcher in background
echo   status : Test and check if Watcher is currently running
echo   stop   : Stop Watcher and its watchdog supervisor
exit /b 1

:do_start
echo Checking if Watcher is already running...
for /f "tokens=*" %%a in ('powershell -NoProfile -Command "(Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' } | Measure-Object).Count"') do set PROC_COUNT=%%a
if not "%PROC_COUNT%"=="0" (
    echo Watcher is already RUNNING.
    goto do_status
)

echo Starting Watcher manually in background...
echo Using JAR: %JAR_PATH%
for %%F in ("%JAR_PATH%") do cd /d "%%~dpF"
start "" "%JAVA_CMD%" -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true -jar "%JAR_PATH%"

timeout /t 2 /nobreak >nul
goto do_status

:do_status
echo Checking Watcher status...
powershell -NoProfile -Command "$procs = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' }; if ($procs) { Write-Host '=========================================='; Write-Host ' Watcher is RUNNING'; Write-Host '=========================================='; $procs | Format-Table ProcessId, Name, CommandLine -AutoSize; exit 0 } else { Write-Host '=========================================='; Write-Host ' Watcher is NOT running.'; Write-Host '=========================================='; exit 1 }"
exit /b %errorlevel%

:do_stop
echo Stopping Watcher manually...
powershell -NoProfile -Command "$procs = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' }; if ($procs) { foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 800; $rem = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' }; if (-not $rem) { Write-Host '=========================================='; Write-Host ' Watcher stopped successfully.'; Write-Host '=========================================='; exit 0 } else { Write-Host 'Warning: Some processes could not be stopped:'; $rem | Format-Table ProcessId, Name; exit 1 } } else { Write-Host '=========================================='; Write-Host ' Watcher is NOT running.'; Write-Host '=========================================='; exit 0 }"
exit /b %errorlevel%
