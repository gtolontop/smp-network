# PTR V3 Folia launcher (PowerShell, foreground)
# Flags: Aikar's G1GC base + G1HeapRegionSize=8M as recommended by the PaperMC
# fill API for this Folia build. Java 25 bundled in <repo>/java/jdk-25.0.2+10.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root 'java\jdk-25.0.2+10'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Push-Location (Join-Path $root 'ptr')
try {
    & "$env:JAVA_HOME\bin\java.exe" `
        -Xms2G -Xmx4G `
        -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 `
        -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch `
        -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 `
        -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 `
        -XX:G1HeapRegionSize=8M `
        -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 `
        -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true `
        -jar folia-26.1.2-8.jar nogui
}
finally {
    Pop-Location
}
