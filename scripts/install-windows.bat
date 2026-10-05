@echo off
:: Watcher Installation Script for Windows (Run as Administrator)
echo ========================================================
echo Installing Watcher System Health Monitor on Windows...
echo ========================================================

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Error: This script must be executed with Administrator privileges.
    exit /b 1
)

set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%syshealthd.jar

if not exist "%JAR_FILE%" (
    if exist "%SCRIPT_DIR%..\target\syshealthd.jar" (
        set JAR_FILE=%SCRIPT_DIR%..\target\syshealthd.jar
    ) else (
        echo Error: syshealthd.jar was not found in the same directory as this script.
        echo Please ensure syshealthd.jar is placed next to this script in: %SCRIPT_DIR%
        exit /b 1
    )
)

set TARGET_DIR=C:\ProgramData\SystemHealth
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
if not exist "%TARGET_DIR%\spool" mkdir "%TARGET_DIR%\spool"
if not exist "%TARGET_DIR%\logs" mkdir "%TARGET_DIR%\logs"

:: Copy files
echo Copying %JAR_FILE% to %TARGET_DIR%...
copy /Y "%JAR_FILE%" "%TARGET_DIR%\syshealthd.jar"

if exist "%SCRIPT_DIR%credentials.json" (
    copy /Y "%SCRIPT_DIR%credentials.json" "%TARGET_DIR%\credentials.json"
) else if exist "%SCRIPT_DIR%..\credentials.json" (
    copy /Y "%SCRIPT_DIR%..\credentials.json" "%TARGET_DIR%\credentials.json"
)

if exist "%SCRIPT_DIR%config.dat" (
    copy /Y "%SCRIPT_DIR%config.dat" "%TARGET_DIR%\config.dat"
) else if exist "%SCRIPT_DIR%..\config.dat" (
    copy /Y "%SCRIPT_DIR%..\config.dat" "%TARGET_DIR%\config.dat"
)

if exist "%SCRIPT_DIR%tokens" (
    xcopy /E /I /Y "%SCRIPT_DIR%tokens" "%TARGET_DIR%\tokens" >nul
) else if exist "%SCRIPT_DIR%..\tokens" (
    xcopy /E /I /Y "%SCRIPT_DIR%..\tokens" "%TARGET_DIR%\tokens" >nul
)

:: Lockdown permissions: SYSTEM & Administrators full access, Standard Users read-only/no-delete
echo Hardening directory permissions...
icacls "%TARGET_DIR%" /inheritance:r /grant:r "SYSTEM":(OI)(CI)F /grant:r "Administrators":(OI)(CI)F /grant:r "Users":(OI)(CI)RX

:: Setup user logon autostart
reg add "HKLM\Software\Microsoft\Windows\CurrentVersion\Run" /v "WinSystemHealth" /t REG_SZ /d "javaw -jar \"%TARGET_DIR%\syshealthd.jar\"" /f

echo ========================================================
echo Watcher installed successfully!
echo Service will launch at user logon.
echo ========================================================
