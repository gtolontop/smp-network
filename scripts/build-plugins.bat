@echo off
REM ============================================================
REM  Build all SMP plugins and Discord bot, deploy to servers
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA_HOME=%BASE_DIR%\java\jdk-25.0.2+10

echo [1/5] Building core Paper plugin...
cd /d "%BASE_DIR%\plugins\core-paper"
call .\gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [2/5] Building core Velocity plugin...
cd /d "%BASE_DIR%\plugins\core-velocity"
call .\gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [3/5] Building AntiCheat Paper plugin...
cd /d "%BASE_DIR%\plugins\anticheat-paper"
call .\gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [4/5] Building SMPLogger plugin...
cd /d "%BASE_DIR%\plugins\smp-logger"
call .\gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [5/5] Building Discord bot...
cd /d "%BASE_DIR%\discord-bot"
call npm install --silent
if errorlevel 1 goto :npm_error
call npm run build
if errorlevel 1 goto :error

echo Deploying jars...
copy /Y "%BASE_DIR%\plugins\core-paper\build\libs\SMPCore-Paper-1.0.0.jar"     "%BASE_DIR%\lobby\plugins\"
copy /Y "%BASE_DIR%\plugins\core-paper\build\libs\SMPCore-Paper-1.0.0.jar"     "%BASE_DIR%\survival\plugins\"
copy /Y "%BASE_DIR%\plugins\core-paper\build\libs\SMPCore-Paper-1.0.0.jar"     "%BASE_DIR%\ptr\plugins\"
copy /Y "%BASE_DIR%\plugins\core-velocity\build\libs\SMPCore-Velocity-1.0.0.jar" "%BASE_DIR%\velocity\plugins\"
REM AntiCheat deployed to both servers: survival fait le taf anti-xray/ESP/movement,
REM lobby ne fait que la detection client (brand + channels + freecam) pour bloquer
REM le transfert vers survival des joueurs avec meteor/wurst/WDL/etc.
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\AntiCheat-Paper-1.0.0.jar" "%BASE_DIR%\lobby\plugins\"
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\AntiCheat-Paper-1.0.0.jar" "%BASE_DIR%\survival\plugins\"
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\AntiCheat-Paper-1.0.0.jar" "%BASE_DIR%\ptr\plugins\"
REM SMPLogger : log full activity + backups, deploy partout pour tout traquer
copy /Y "%BASE_DIR%\plugins\smp-logger\build\libs\SMPLogger-Paper-1.0.0.jar"   "%BASE_DIR%\survival\plugins\"
copy /Y "%BASE_DIR%\plugins\smp-logger\build\libs\SMPLogger-Paper-1.0.0.jar"   "%BASE_DIR%\lobby\plugins\"
copy /Y "%BASE_DIR%\plugins\smp-logger\build\libs\SMPLogger-Paper-1.0.0.jar"   "%BASE_DIR%\ptr\plugins\"

if not exist "%BASE_DIR%\shared-data\players" mkdir "%BASE_DIR%\shared-data\players"

echo Done.
exit /b 0

:error
echo Build failed.
exit /b 1

:npm_error
echo npm install failed.
exit /b 1
