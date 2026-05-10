# Minecraft RCON client (single-shot stop) - PowerShell
param(
    [string]$RconHost = "127.0.0.1",
    [int]$Port = 25578,
    [Parameter(Mandatory)] [string]$Password,
    [string]$Command = "stop"
)

function Send-RconPacket {
    param($Stream, [int]$Id, [int]$Type, [string]$Payload)
    $payloadBytes = [System.Text.Encoding]::ASCII.GetBytes($Payload)
    $length = 10 + $payloadBytes.Length
    $bw = [System.IO.BinaryWriter]::new($Stream, [System.Text.Encoding]::ASCII, $true)
    $bw.Write([int32]$length)
    $bw.Write([int32]$Id)
    $bw.Write([int32]$Type)
    if ($payloadBytes.Length -gt 0) { $bw.Write($payloadBytes) }
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
}

function Read-RconPacket {
    param($Stream)
    $br = [System.IO.BinaryReader]::new($Stream, [System.Text.Encoding]::ASCII, $true)
    $length = $br.ReadInt32()
    $id = $br.ReadInt32()
    $type = $br.ReadInt32()
    $payloadBytes = New-Object byte[] ($length - 10)
    if ($payloadBytes.Length -gt 0) {
        $read = 0
        while ($read -lt $payloadBytes.Length) {
            $r = $Stream.Read($payloadBytes, $read, $payloadBytes.Length - $read)
            if ($r -le 0) { break }
            $read += $r
        }
    }
    $null = $br.ReadByte()
    $null = $br.ReadByte()
    return @{Id=$id; Type=$type; Payload=[System.Text.Encoding]::ASCII.GetString($payloadBytes)}
}

$client = New-Object System.Net.Sockets.TcpClient($RconHost, $Port)
$client.NoDelay = $true
$stream = $client.GetStream()

Send-RconPacket $stream 1 3 $Password
$resp = Read-RconPacket $stream
if ($resp.Id -eq -1) {
    Write-Error "RCON auth failed"
    $client.Close()
    exit 1
}

Send-RconPacket $stream 2 2 $Command
$resp = Read-RconPacket $stream
Write-Output ("RCON> " + $resp.Payload)

$client.Close()
