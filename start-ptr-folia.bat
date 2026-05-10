@echo off
REM PTR V3 Folia launcher (Windows)
setlocal
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%java\jdk-25.0.2+10"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%ROOT%ptr"
"%JAVA_HOME%\bin\java" -Xms2G -Xmx4G ^
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
  -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 ^
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 ^
  -XX:G1HeapRegionSize=8M ^
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 ^
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true ^
  -jar folia-26.1.2-8.jar nogui
endlocal
