@echo off
setlocal

:: Watcher Status / Test Script for Windows
echo Checking Watcher status...
powershell -NoProfile -Command "$procs = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' }; if ($procs) { Write-Host '=========================================='; Write-Host ' Watcher is RUNNING'; Write-Host '=========================================='; $procs | Format-Table ProcessId, Name, CommandLine -AutoSize; exit 0 } else { Write-Host '=========================================='; Write-Host ' Watcher is NOT running.'; Write-Host '=========================================='; exit 1 }"
exit /b %errorlevel%
