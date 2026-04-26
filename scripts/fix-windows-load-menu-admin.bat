@echo off
setlocal EnableExtensions

REM ============================================================
REM  Windows load relief menu for local SMP hosting.
REM  Run this file as Administrator.
REM ============================================================

set BASE_DIR=%~dp0..

net session >nul 2>&1
if not "%errorlevel%"=="0" (
    echo.
    echo This script must be run as Administrator.
    echo Right-click it, then choose "Run as administrator".
    echo.
    pause
    exit /b 1
)

:menu
cls
echo ============================================
echo   Windows load relief - admin menu
echo ============================================
echo.
echo Base folder: %BASE_DIR%
echo.
echo  1. Stop MariaDB and set startup to Manual
echo  2. Stop Ollama
echo  3. Stop iCUE updater service
echo  4. Add Windows Defender exclusions for this SMP folder
echo  5. Disable broken ASUS LightingService
echo  6. Restart SMP network to apply Java memory changes
echo  7. Run safe quick relief steps 1-4
echo  8. Show current heavy processes
echo  9. Exit
echo.
choice /C 123456789 /N /M "Choose an action: "

if errorlevel 9 goto end
if errorlevel 8 goto heavy
if errorlevel 7 goto quick
if errorlevel 6 goto restart_smp
if errorlevel 5 goto lighting
if errorlevel 4 goto defender
if errorlevel 3 goto icue
if errorlevel 2 goto ollama
if errorlevel 1 goto mariadb

:mariadb
echo.
echo [MariaDB] Checking service...
sc query MariaDB >nul 2>&1
if errorlevel 1 (
    echo MariaDB service not found.
) else (
    net stop MariaDB
    echo Setting MariaDB startup to Manual...
    sc config MariaDB start= demand
)
pause
goto menu

:ollama
echo.
echo [Ollama] Stopping process if running...
taskkill /IM ollama.exe /F >nul 2>&1
if errorlevel 1 (
    echo Ollama was not running.
) else (
    echo Ollama stopped.
)
pause
goto menu

:icue
echo.
echo [iCUE] Stopping updater service if present...
sc query iCUEUpdateService >nul 2>&1
if errorlevel 1 (
    echo iCUEUpdateService not found.
) else (
    net stop iCUEUpdateService
)
pause
goto menu

:defender
echo.
echo [Defender] Adding exclusions for server workspace and bundled Java...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-MpPreference -ExclusionPath '%BASE_DIR%'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-MpPreference -ExclusionPath '%BASE_DIR%\java'"
echo Defender exclusions requested.
pause
goto menu

:lighting
echo.
echo [ASUS LightingService] This only disables the broken Aura lighting service.
echo It can stop repeated DCOM/service errors, but RGB/Aura features may stop working.
choice /C YN /N /M "Disable LightingService now? [Y/N]: "
if errorlevel 2 goto menu
sc query LightingService >nul 2>&1
if errorlevel 1 (
    echo LightingService not found.
) else (
    net stop LightingService
    sc config LightingService start= disabled
)
pause
goto menu

:restart_smp
echo.
echo [SMP] This stops and starts the whole Minecraft network.
echo It applies the new Survival Java memory settings.
choice /C YN /N /M "Restart SMP network now? [Y/N]: "
if errorlevel 2 goto menu
call "%BASE_DIR%\scripts\stop-all.bat"
call "%BASE_DIR%\scripts\start-all.bat"
pause
goto menu

:quick
echo.
echo [Quick relief] Running steps 1-4...
call :quick_mariadb
call :quick_ollama
call :quick_icue
call :quick_defender
echo.
echo Quick relief done. Restart the SMP network from menu option 6 when ready.
pause
goto menu

:quick_mariadb
sc query MariaDB >nul 2>&1
if not errorlevel 1 (
    net stop MariaDB
    sc config MariaDB start= demand
)
exit /b 0

:quick_ollama
taskkill /IM ollama.exe /F >nul 2>&1
exit /b 0

:quick_icue
sc query iCUEUpdateService >nul 2>&1
if not errorlevel 1 net stop iCUEUpdateService
exit /b 0

:quick_defender
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-MpPreference -ExclusionPath '%BASE_DIR%'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-MpPreference -ExclusionPath '%BASE_DIR%\java'"
exit /b 0

:heavy
echo.
echo [Processes] Current heavy memory processes:
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Process | Sort-Object PrivateMemorySize -Descending | Select-Object -First 20 Name,Id,@{n='PrivateGB';e={[math]::Round($_.PrivateMemorySize64/1GB,2)}},@{n='WorkingGB';e={[math]::Round($_.WorkingSet64/1GB,2)}},Path | Format-Table -AutoSize"
pause
goto menu

:end
endlocal
