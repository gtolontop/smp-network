param(
    [string]$RconHost = "127.0.0.1",
    [int]$Port = 25578,
    [Parameter(Mandatory)] [string]$Password,
    [Parameter(Mandatory)] [string]$Command
)

function Build-Packet {
    param([int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $size = 4 + 4 + $bodyBytes.Length + 2
    $buf = New-Object byte[] (4 + $size)
    [System.BitConverter]::GetBytes([int32]$size).CopyTo($buf, 0)
    [System.BitConverter]::GetBytes([int32]$Id).CopyTo($buf, 4)
    [System.BitConverter]::GetBytes([int32]$Type).CopyTo($buf, 8)
    if ($bodyBytes.Length -gt 0) { $bodyBytes.CopyTo($buf, 12) }
    $buf[$buf.Length - 2] = 0
    $buf[$buf.Length - 1] = 0
    return ,$buf
}

function Read-Exact {
    param($Stream, [int]$Count)
    $buf = New-Object byte[] $Count
    $read = 0
    while ($read -lt $Count) {
        $r = $Stream.Read($buf, $read, $Count - $read)
        if ($r -le 0) { throw "stream closed at $read/$Count" }
        $read += $r
    }
    return ,$buf
}

function Read-Packet {
    param($Stream)
    $sizeBytes = Read-Exact $Stream 4
    $size = [System.BitConverter]::ToInt32($sizeBytes, 0)
    $rest = Read-Exact $Stream $size
    $id = [System.BitConverter]::ToInt32($rest, 0)
    $type = [System.BitConverter]::ToInt32($rest, 4)
    $bodyLen = $size - 4 - 4 - 2
    $body = ""
    if ($bodyLen -gt 0) { $body = [System.Text.Encoding]::ASCII.GetString($rest, 8, $bodyLen) }
    return @{Id=$id; Type=$type; Body=$body}
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($RconHost, $Port)
$client.SendTimeout = 5000
$client.ReceiveTimeout = 10000
$stream = $client.GetStream()

# Auth
$authPkt = Build-Packet 1 3 $Password
$stream.Write($authPkt, 0, $authPkt.Length)
$stream.Flush()
$authResp = Read-Packet $stream
if ($authResp.Id -eq -1) {
    Write-Error "RCON auth failed (wrong password?)"
    $client.Close()
    exit 1
}

# Command
$cmdPkt = Build-Packet 2 2 $Command
$stream.Write($cmdPkt, 0, $cmdPkt.Length)
$stream.Flush()
try {
    $cmdResp = Read-Packet $stream
    Write-Output ("RCON> " + $cmdResp.Body)
} catch {
    Write-Output "RCON> (no response, command likely terminated server)"
}

try { $client.Close() } catch {}
