param(
    [string]$RconHost = "127.0.0.1",
    [int]$Port = 25578,
    [Parameter(Mandatory)] [string]$Password
)
$ErrorActionPreference = 'Stop'
$rconCmd = Join-Path $PSScriptRoot "rcon-cmd.ps1"
& $rconCmd -RconHost $RconHost -Port $Port -Password $Password -Command "save-all flush"
& $rconCmd -RconHost $RconHost -Port $Port -Password $Password -Command "stop"
