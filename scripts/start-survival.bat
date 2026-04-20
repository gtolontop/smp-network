@echo off
REM ============================================================
REM  Start only the Survival server (Paper)
REM  Velocity + Lobby ne sont pas touches
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA=%BASE_DIR%\java\jdk-25.0.2+10\bin\java.exe

if not exist "%JAVA%" (
    echo ERROR: Java 25 not found at %JAVA%
    pause
    exit /b 1
)

echo ============================================
echo   Starting Survival server (Paper)
echo   Java: %JAVA%
echo ============================================

start "Survival Server" cmd /k "cd /d %BASE_DIR%\survival && "%JAVA%" --enable-native-access=ALL-UNNAMED -Xms32G -Xmx32G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=32M -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseStringDeduplication -jar paper.jar --nogui"

echo.
echo Survival lance dans sa propre fenetre.
