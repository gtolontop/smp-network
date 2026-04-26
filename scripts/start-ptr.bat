@echo off
REM ============================================================
REM  Start the PTR server (Paper)
REM  Public creative test realm with isolated inventories
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA=%BASE_DIR%\java\jdk-25.0.2+10\bin\java.exe

if not exist "%JAVA%" (
    echo ERROR: Java 25 not found at %JAVA%
    pause
    exit /b 1
)

if not exist "%BASE_DIR%\ptr\paper.jar" (
    echo ERROR: ptr is missing paper.jar
    pause
    exit /b 1
)

echo ============================================
echo   Starting PTR server (Paper)
echo   Port: 25568
echo   Sync: shared-data-ptr
echo   Java: %JAVA%
echo ============================================

start "PTR Server" cmd /k "cd /d %BASE_DIR%\ptr && "%JAVA%" --enable-native-access=ALL-UNNAMED -Dsmp.server.type=ptr -Xms1G -Xmx1G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseStringDeduplication -jar paper.jar --nogui"

echo.
echo PTR launched in its own window.
echo Join it through Velocity with /ptr.
