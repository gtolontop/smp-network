@echo off
REM ============================================================
REM  Windows load relief for local SMP hosting.
REM  Run this file as Administrator.
REM ============================================================

set BASE_DIR=%~dp0..

echo ============================================
echo   Windows load relief
echo ============================================
echo.

echo [1/4] Stopping MariaDB if it is running...
sc query MariaDB >nul 2>&1
if not errorlevel 1 (
    net stop MariaDB
    echo Setting MariaDB startup to Manual...
    sc config MariaDB start= demand
) else (
    echo MariaDB service not found.
)

echo.
echo [2/4] Stopping Ollama if it is running...
taskkill /IM ollama.exe /F >nul 2>&1
if errorlevel 1 (
    echo Ollama was not running.
) else (
    echo Ollama stopped.
)

echo.
echo [3/4] Stopping non-essential iCUE updater...
sc query iCUEUpdateService >nul 2>&1
if not errorlevel 1 (
    net stop iCUEUpdateService >nul 2>&1
)

echo.
echo [4/4] Adding Windows Defender exclusions for this server workspace...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-MpPreference -ExclusionPath '%BASE_DIR%'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-MpPreference -ExclusionPath '%BASE_DIR%\java'"

echo.
echo Done. Restart the SMP network afterwards to apply the Java memory fixes:
echo   scripts\stop-all.bat
echo   scripts\start-all.bat
echo.
pause
