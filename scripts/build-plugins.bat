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

echo Deploying jars...
copy /Y "%BASE_DIR%\plugins\core-paper\build\libs\SMPCore-Paper-1.0.0.jar"     "%BASE_DIR%\lobby\plugins\"
copy /Y "%BASE_DIR%\plugins\core-paper\build\libs\SMPCore-Paper-1.0.0.jar"     "%BASE_DIR%\survival\plugins\"
copy /Y "%BASE_DIR%\plugins\core-velocity\build\libs\SMPCore-Velocity-1.0.0.jar" "%BASE_DIR%\velocity\plugins\"
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\AntiCheat-Paper-1.0.0.jar" "%BASE_DIR%\survival\plugins\"

if not exist "%BASE_DIR%\shared-data\players" mkdir "%BASE_DIR%\shared-data\players"

echo Done.
exit /b 0

:error
echo Build failed.
exit /b 1
