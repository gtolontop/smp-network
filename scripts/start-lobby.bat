@echo off
REM ============================================================
REM  Start only the Lobby server (Paper)
REM  Velocity + Survival ne sont pas touches
REM ============================================================

set BASE_DIR=%~dp0..
set JAVA=%BASE_DIR%\java\jdk-25.0.2+10\bin\java.exe

if not exist "%JAVA%" (
    echo ERROR: Java 25 not found at %JAVA%
    pause
    exit /b 1
)

echo ============================================
echo   Starting Lobby server (Paper)
echo   Java: %JAVA%
echo ============================================

start "Lobby Server" cmd /k "cd /d %BASE_DIR%\lobby && "%JAVA%" --enable-native-access=ALL-UNNAMED -Xms1G -Xmx2G -jar paper.jar --nogui"

echo.
echo Lobby lance dans sa propre fenetre.
