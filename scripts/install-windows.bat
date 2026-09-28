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

set TARGET_DIR=C:\ProgramData\SystemHealth
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
if not exist "%TARGET_DIR%\spool" mkdir "%TARGET_DIR%\spool"
if not exist "%TARGET_DIR%\logs" mkdir "%TARGET_DIR%\logs"

:: Copy files
copy /Y "%~dp0..\target\syshealthd.jar" "%TARGET_DIR%\syshealthd.jar"
if exist "%~dp0..\credentials.json" copy /Y "%~dp0..\credentials.json" "%TARGET_DIR%\credentials.json"

:: Lockdown permissions: SYSTEM & Administrators full access, Standard Users read-only/no-delete
echo Hardening directory permissions...
icacls "%TARGET_DIR%" /inheritance:r /grant:r "SYSTEM":(OI)(CI)F /grant:r "Administrators":(OI)(CI)F /grant:r "Users":(OI)(CI)RX

:: Setup user logon autostart
reg add "HKLM\Software\Microsoft\Windows\CurrentVersion\Run" /v "WinSystemHealth" /t REG_SZ /d "javaw -jar \"%TARGET_DIR%\syshealthd.jar\"" /f

echo ========================================================
echo Watcher installed successfully!
echo Service will launch at user logon.
echo ========================================================
