@echo off
REM ============================================================
REM  Build AntiCheat-Paper and push to lobby + survival plugins/
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA_HOME=%BASE_DIR%\java\jdk-25.0.2+10
set JAR=AntiCheat-Paper-1.0.0.jar

echo [1/2] Building AntiCheat-Paper...
cd /d "%BASE_DIR%\plugins\anticheat-paper"
call gradlew.bat shadowJar
if errorlevel 1 goto :error

echo [2/2] Deploying %JAR%...
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\%JAR%" "%BASE_DIR%\lobby\plugins\"
if errorlevel 1 goto :error
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\%JAR%" "%BASE_DIR%\survival\plugins\"
if errorlevel 1 goto :error
copy /Y "%BASE_DIR%\plugins\anticheat-paper\build\libs\%JAR%" "%BASE_DIR%\ptr\plugins\"
if errorlevel 1 goto :error

echo Done.
pause
exit /b 0

:error
echo Build/deploy failed.
exit /b 1
