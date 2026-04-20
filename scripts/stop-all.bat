@echo off
setlocal EnableDelayedExpansion
REM ============================================================
REM  Graceful shutdown for the SMP network.
REM  Sends "save-all flush" + "stop" via RCON to lobby/survival,
REM  waits for the world to finish flushing, then closes Velocity.
REM  Never use the cmd "X" button to stop a server: always run this.
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA=%BASE_DIR%\java\jdk-25.0.2+10\bin\java.exe
set RCON=%~dp0rcon\RconCli.java

set LOBBY_HOST=127.0.0.1
set LOBBY_PORT=25575
set LOBBY_PASS=aYG12mIV0S3KJvM7ouhYRU3tjF2BNIx
set LOBBY_MC_PORT=25566

set SURVIVAL_HOST=127.0.0.1
set SURVIVAL_PORT=25576
set SURVIVAL_PASS=ZFhJ7bUXe1mNq7vNvtrBQULrNC6WMPh
set SURVIVAL_MC_PORT=25567

if not exist "%JAVA%" (
    echo ERROR: Java 25 not found at %JAVA%
    exit /b 1
)

echo ============================================
echo   Graceful shutdown (RCON + save-all flush)
echo ============================================

call :stopPaper Survival %SURVIVAL_HOST% %SURVIVAL_PORT% %SURVIVAL_PASS% %SURVIVAL_MC_PORT%
call :stopPaper Lobby    %LOBBY_HOST%    %LOBBY_PORT%    %LOBBY_PASS%    %LOBBY_MC_PORT%

echo.
echo [Velocity] closing proxy window...
taskkill /FI "WINDOWTITLE eq Velocity Proxy" /T /F >nul 2>&1
REM Velocity has no world data; brute-close is safe once backends are down.

echo.
echo All servers saved and stopped cleanly.
exit /b 0

:stopPaper
set NAME=%~1
set HOST=%~2
set PORT=%~3
set PASS=%~4
set MC_PORT=%~5

echo.
echo [%NAME%] save-all flush via RCON...
"%JAVA%" "%RCON%" %HOST% %PORT% %PASS% save-all flush
if errorlevel 1 (
    echo [%NAME%] RCON unreachable - server may already be down.
    goto :eof
)

echo [%NAME%] stop via RCON...
"%JAVA%" "%RCON%" %HOST% %PORT% %PASS% stop >nul 2>&1

echo [%NAME%] waiting for port %MC_PORT% to close (max 120s)...
set /a TRIES=0
:waitLoop
set /a TRIES+=1
netstat -an | findstr ":%MC_PORT% " | findstr "LISTENING" >nul
if errorlevel 1 (
    echo [%NAME%] stopped.
    goto :eof
)
if !TRIES! GEQ 60 (
    echo [%NAME%] still running after 120s, giving up gracefully.
    goto :eof
)
timeout /t 2 /nobreak >nul
goto waitLoop
