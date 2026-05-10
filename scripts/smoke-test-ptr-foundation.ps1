# Smoke test: boot Folia 26.1.2 with PtrFoundation, watch logs for
# "Done (" and zero exceptions during the first 30 s, then RCON-stop.
#
# Exit codes:
#   0 — Done(...) reported, no exception traces seen, /ptrf info responded
#   1 — boot timeout or exception trace seen
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$ptrDir   = Join-Path $root 'ptr'
$jar      = Join-Path $ptrDir 'folia-26.1.2-8.jar'
$pluginsDir = Join-Path $ptrDir 'plugins'
$pluginJar = Get-ChildItem (Join-Path $root 'plugins\ptr-foundation\build\libs') -Filter 'PtrFoundation-*.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not (Test-Path $jar))            { throw "Folia jar missing: $jar — download per ptr/README.md" }
if (-not $pluginJar)                  { throw "PtrFoundation jar missing — run ./gradlew build first" }

if (-not (Test-Path $pluginsDir))     { New-Item -ItemType Directory $pluginsDir -Force | Out-Null }
Copy-Item $pluginJar.FullName (Join-Path $pluginsDir 'PtrFoundation.jar') -Force

$rcon = Join-Path $root 'scripts\rcon-cmd.ps1'
$rconPwd = (Select-String -Path (Join-Path $ptrDir 'server.properties') -Pattern '^rcon\.password=(.+)$').Matches.Groups[1].Value
$rconPort = [int]((Select-String -Path (Join-Path $ptrDir 'server.properties') -Pattern '^rcon\.port=(\d+)$').Matches.Groups[1].Value)

$javaHome = Join-Path $root 'java\jdk-25.0.2+10'
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

# Wipe world to make boot deterministic.
foreach ($w in 'world','world_nether','world_the_end') {
    $p = Join-Path $ptrDir $w
    if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}

$logFile = Join-Path $env:TEMP "ptr-foundation-smoke-$([guid]::NewGuid().ToString('N').Substring(0,8)).log"
$proc = Start-Process -FilePath (Join-Path $javaHome 'bin\java.exe') -ArgumentList @(
    '-Xms2G','-Xmx4G',
    '-XX:+UseG1GC','-XX:+ParallelRefProcEnabled','-XX:MaxGCPauseMillis=200',
    '-XX:+UnlockExperimentalVMOptions','-XX:+DisableExplicitGC','-XX:+AlwaysPreTouch',
    '-XX:G1HeapRegionSize=8M',
    '-jar', $jar, 'nogui'
) -WorkingDirectory $ptrDir -RedirectStandardOutput $logFile -PassThru -NoNewWindow

$timeoutSeconds = 60
$elapsed = 0
$bootReady = $false
$hadException = $false

while ($elapsed -lt $timeoutSeconds) {
    Start-Sleep -Seconds 2
    $elapsed += 2
    if (Test-Path $logFile) {
        $log = Get-Content $logFile -Raw
        if ($log -match 'Done \(\d') { $bootReady = $true; break }
        if ($log -match 'Exception in server tick loop|java\.lang\.\w+Exception') { $hadException = $true; break }
    }
}

$bootElapsedSec = $elapsed

# Try /ptrf info via RCON
$rconOk = $false
if ($bootReady) {
    try {
        & $rcon -Password $rconPwd -Port $rconPort -Command 'ptrf info' 2>&1 | Out-Null
        $rconOk = $true
    } catch {
        Write-Warning "RCON /ptrf info failed: $_"
    }
}

# Try stopping cleanly via RCON, falling back to process kill.
try {
    if ($rconOk) {
        & $rcon -Password $rconPwd -Port $rconPort -Command 'stop' 2>&1 | Out-Null
    }
} catch {}
$stopWait = 0
while (-not $proc.HasExited -and $stopWait -lt 30) {
    Start-Sleep -Seconds 2
    $stopWait += 2
}
if (-not $proc.HasExited) { try { Stop-Process -Id $proc.Id -Force } catch {} }

$logTail = (Get-Content $logFile -Tail 80) -join "`n"

Write-Host '--- last 80 log lines ---'
Write-Host $logTail
Write-Host '--- summary ---'
Write-Host "bootReady=$bootReady hadException=$hadException bootElapsedSec=$bootElapsedSec rconOk=$rconOk"

if ($bootReady -and -not $hadException -and $rconOk) {
    Write-Host 'SMOKE TEST PASSED'
    exit 0
}
Write-Host 'SMOKE TEST FAILED'
exit 1
