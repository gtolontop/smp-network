@echo off
setlocal EnableDelayedExpansion
REM Graceful stop for the PTR server only (RCON + save-all flush).

set BASE_DIR=%~dp0..
set JAVA=%BASE_DIR%\java\jdk-25.0.2+10\bin\java.exe
set RCON=%~dp0rcon\RconCli.java

set HOST=127.0.0.1
set PORT=25578
set PASS=43b9fc9853494223a5d6889cc4e9b025
set MC_PORT=25568

if not exist "%JAVA%" (
    echo ERROR: Java 25 not found at %JAVA%
    exit /b 1
)

echo [PTR] save-all flush via RCON...
"%JAVA%" "%RCON%" %HOST% %PORT% %PASS% save-all flush
if errorlevel 1 (
    echo [PTR] RCON unreachable - server may already be down.
    exit /b 0
)

echo [PTR] stop via RCON...
"%JAVA%" "%RCON%" %HOST% %PORT% %PASS% stop >nul 2>&1

echo [PTR] waiting for port %MC_PORT% to close (max 120s)...
set /a TRIES=0
:waitLoop
set /a TRIES+=1
netstat -an | findstr ":%MC_PORT% " | findstr "LISTENING" >nul
if errorlevel 1 (
    echo [PTR] stopped.
    exit /b 0
)
if !TRIES! GEQ 60 (
    echo [PTR] still running after 120s, giving up gracefully.
    exit /b 1
)
timeout /t 2 /nobreak >nul
goto waitLoop
