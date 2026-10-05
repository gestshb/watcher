@echo off
setlocal

:: Watcher Stop Script for Windows
echo Stopping Watcher manually...
powershell -NoProfile -Command "$procs = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' }; if ($procs) { foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 800; $rem = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -like '*syshealthd.jar*' }; if (-not $rem) { Write-Host '=========================================='; Write-Host ' Watcher stopped successfully.'; Write-Host '=========================================='; exit 0 } else { Write-Host 'Warning: Some processes could not be stopped:'; $rem | Format-Table ProcessId, Name; exit 1 } } else { Write-Host '=========================================='; Write-Host ' Watcher is NOT running.'; Write-Host '=========================================='; exit 0 }"
exit /b %errorlevel%
