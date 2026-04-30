@echo off
REM ============================================================
REM  Start all servers: Velocity + Lobby (Paper) + Survival (Paper)
REM  Uses bundled Java 25 (required for MC 26.1.2)
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA=%BASE_DIR%\java\jdk-25.0.2+10\bin\java.exe
set PLAYIT="C:\Program Files\playit_gg\bin\playit.exe"

REM Check Java exists
if not exist "%JAVA%" (
    echo ERROR: Java 25 not found at %JAVA%
    echo Run scripts\download.sh to install it, or download from https://adoptium.net/
    pause
    exit /b 1
)

echo ============================================
echo   Starting SMP Network (MC 26.1.2)
echo   All servers running Paper
echo   Java: %JAVA%
echo ============================================

REM --- Start Discord Bot (bridge host — must be up before plugins) ---
echo [0/4] Starting Discord bot...
start "Discord Bot" cmd /k "cd /d %BASE_DIR%\discord-bot && npm start"

REM Let the bridge WebSocket come up before plugins try to connect
timeout /t 8 /nobreak >nul

REM --- Start Velocity Proxy ---
echo [1/4] Starting Velocity proxy...
start "Velocity Proxy" cmd /k "cd /d %BASE_DIR%\velocity && "%JAVA%" --enable-native-access=ALL-UNNAMED -Xms512M -Xmx512M -jar velocity.jar"

REM Wait a moment for proxy to initialize
timeout /t 5 /nobreak >nul

REM --- Start Lobby (Paper) ---
echo [2/4] Starting Lobby server (Paper)...
start "Lobby Server" cmd /k "cd /d %BASE_DIR%\lobby && "%JAVA%" --enable-native-access=ALL-UNNAMED -Xms1G -Xmx1G -jar paper.jar --nogui"

REM Wait a moment
timeout /t 5 /nobreak >nul

REM --- Start Survival (Paper) ---
echo [3/4] Starting Survival server (Paper)...
start "Survival Server" cmd /k "cd /d %BASE_DIR%\survival && "%JAVA%" --enable-native-access=ALL-UNNAMED -Xms4G -Xmx16G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseStringDeduplication -Daikars.new.flags=true -Dusing.aikars.flags=https://mcflags.emc.gs -jar paper.jar --nogui"

REM Wait a moment
timeout /t 5 /nobreak >nul

REM --- Start PTR (Paper) ---
echo [4/4] Starting PTR server (Paper)...
start "PTR Server" cmd /k "cd /d %BASE_DIR%\ptr && "%JAVA%" --enable-native-access=ALL-UNNAMED -Dsmp.server.type=ptr -Xms1G -Xmx1G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseStringDeduplication -jar paper.jar --nogui"

REM Wait for Velocity to be listening before opening tunnel
timeout /t 10 /nobreak >nul

REM --- Start Playit.gg tunnel ---
echo [+] Starting Playit.gg tunnel...
if exist %PLAYIT% (
    start "Playit Tunnel" cmd /k %PLAYIT%
) else (
    echo WARNING: Playit not found at %PLAYIT% - skipping tunnel
)

echo.
echo ============================================
echo   All servers started! (All Paper)
echo   Velocity:  localhost:25565
echo   Lobby:     localhost:25566
echo   Survival:  localhost:25567
echo   PTR:       localhost:25568
echo   Tunnel:    check Playit window for address
echo --------------------------------------------
echo   TO STOP: run scripts\stop-all.bat
echo   DO NOT close windows with [X] - risks rollback.
echo ============================================
pause
