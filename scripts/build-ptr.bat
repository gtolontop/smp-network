@echo off
REM ============================================================
REM  Build both SMPCore plugins and deploy to server folders
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA_HOME=%BASE_DIR%\java\jdk-25.0.2+10

echo [1/3] Building core Paper plugin...
cd /d "%BASE_DIR%\plugins\core-paper"
call gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [2/3] Building core Velocity plugin...
cd /d "%BASE_DIR%\plugins\core-velocity"
call gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [3/3] Building AntiCheat Paper plugin...
cd /d "%BASE_DIR%\plugins\anticheat-paper"
call gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [4/5] Building SMPLogger plugin...
cd /d "%BASE_DIR%\plugins\smp-logger"
call .\gradlew.bat shadowJar
if errorlevel 1 goto :error

echo Deploying jars...
copy /Y "%BASE_DIR%\plugins\core-paper\build\libs\SMPCore-Paper-1.0.0.jar"     "%BASE_DIR%\ptr\plugins\"
REM AntiCheat deployed to both servers: survive fait le taf anti-xray/ESP/movement,
REM lobby ne fait que la detection client (brand + channels + freecam) pour bloquer
REM le transfert vers survival des joueurs avec meteor/wurst/WDL/etc.
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\AntiCheat-Paper-1.0.0.jar" "%BASE_DIR%\ptr\plugins\"
copy /Y "%BASE_DIR%\plugins\smp-logger\build\libs\SMPLogger-Paper-1.0.0.jar"   "%BASE_DIR%\ptr\plugins\"


if not exist "%BASE_DIR%\shared-data-ptr\players" mkdir "%BASE_DIR%\shared-data-ptr\players"

pause

echo Done.
exit /b 0

:error
echo Build failed.
exit /b 1
