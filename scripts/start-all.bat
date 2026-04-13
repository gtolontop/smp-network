@echo off
REM ============================================================
REM  Start all servers: Velocity + Lobby + Survival
REM  Run from the project root or scripts/ directory.
REM ============================================================

set BASE_DIR=%~dp0..

echo ============================================
echo   Starting SMP Network
echo ============================================

REM --- Start Velocity Proxy ---
echo [1/3] Starting Velocity proxy...
start "Velocity Proxy" cmd /k "cd /d %BASE_DIR%\velocity && java -Xms512M -Xmx512M -jar velocity.jar"

REM Wait a moment for proxy to initialize
timeout /t 5 /nobreak >nul

REM --- Start Lobby (Paper) ---
echo [2/3] Starting Lobby server...
start "Lobby Server" cmd /k "cd /d %BASE_DIR%\lobby && java -Xms1G -Xmx2G -jar paper.jar --nogui"

REM Wait a moment
timeout /t 5 /nobreak >nul

REM --- Start Survival (Folia) ---
echo [3/3] Starting Survival server...
start "Survival Server" cmd /k "cd /d %BASE_DIR%\survival && java -Xms8G -Xmx16G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -jar folia.jar --nogui"

echo.
echo ============================================
echo   All servers started!
echo   Velocity:  localhost:25565
echo   Lobby:     localhost:25566
echo   Survival:  localhost:25567
echo ============================================
pause
