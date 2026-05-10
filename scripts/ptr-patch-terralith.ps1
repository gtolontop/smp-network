# PTR v2 — patcher Terralith fork in-place.
# Applique color shift HSV deterministe par biome + climat shift + surface tweaks
# + features overrides sur Skylands.
# Idempotent: re-extrait le zip avant de patcher pour repartir d'un state propre.

[CmdletBinding()]
param(
    [string]$PtrRoot = "C:\Users\teamr\Desktop\miencraft\ptr",
    [string]$ZipName = "Terralith_26.1_v2.6.2.zip",
    [string]$OutFolder = "terralith_v2"
)

$ErrorActionPreference = 'Stop'
$src = Join-Path $PtrRoot "world\datapacks\$ZipName"
$dst = Join-Path $PtrRoot "world\datapacks\$OutFolder"

if (-not (Test-Path $src)) { throw "Terralith zip introuvable: $src" }

Write-Host "[1/6] Extraction $ZipName -> $OutFolder" -ForegroundColor Cyan
if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
Expand-Archive -Path $src -DestinationPath $dst -Force

# ----- Helpers -----

function Get-BiomeSeed([string]$name) {
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $hash = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($name))
    $md5.Dispose()
    # Use signed Int32 to avoid overflow when XORing
    return [System.BitConverter]::ToInt32($hash, 0)
}

function ConvertTo-HSV([long]$rgb) {
    # mask to 24 bits in case ARGB or wider int was stored
    $rgb = $rgb -band 0xFFFFFF
    $r = (($rgb -shr 16) -band 0xFF) / 255.0
    $g = (($rgb -shr 8) -band 0xFF) / 255.0
    $b = ($rgb -band 0xFF) / 255.0
    $max = [Math]::Max($r, [Math]::Max($g, $b))
    $min = [Math]::Min($r, [Math]::Min($g, $b))
    $d = $max - $min
    $h = 0.0
    if ($d -gt 1e-6) {
        if ($max -eq $r) { $h = 60.0 * ((($g - $b) / $d) % 6) }
        elseif ($max -eq $g) { $h = 60.0 * ((($b - $r) / $d) + 2) }
        else { $h = 60.0 * ((($r - $g) / $d) + 4) }
    }
    if ($h -lt 0) { $h += 360 }
    $s = if ($max -le 1e-6) { 0.0 } else { $d / $max }
    $v = $max
    return @{H=$h; S=$s; V=$v}
}

function ConvertFrom-HSV($hsv) {
    $h = $hsv.H % 360; if ($h -lt 0) { $h += 360 }
    $s = [Math]::Max(0, [Math]::Min(1, $hsv.S))
    $v = [Math]::Max(0, [Math]::Min(1, $hsv.V))
    $c = $v * $s
    $x = $c * (1 - [Math]::Abs((($h / 60.0) % 2) - 1))
    $m = $v - $c
    $r = 0.0; $g = 0.0; $b = 0.0
    switch ([int]([Math]::Floor($h / 60.0))) {
        0 { $r = $c; $g = $x; $b = 0 }
        1 { $r = $x; $g = $c; $b = 0 }
        2 { $r = 0; $g = $c; $b = $x }
        3 { $r = 0; $g = $x; $b = $c }
        4 { $r = $x; $g = 0; $b = $c }
        default { $r = $c; $g = 0; $b = $x }
    }
    $R = [int]([Math]::Round(($r + $m) * 255))
    $G = [int]([Math]::Round(($g + $m) * 255))
    $B = [int]([Math]::Round(($b + $m) * 255))
    return ($R -shl 16) -bor ($G -shl 8) -bor $B
}

function Shift-Color([long]$rgb, [int]$seed, [int]$channelOffset) {
    if ($null -eq $rgb -or $rgb -eq 0) { return $rgb }
    $hsv = ConvertTo-HSV $rgb
    # All mixing in [long] to avoid Int32 overflow exceptions
    $sl = [long]$seed
    $cl = [long]$channelOffset
    # Hue shift: 30 to 330 deg, deterministic per biome+channel
    $mix1 = ($sl -bxor ($cl * 31337)) -band 0xFFFFL
    $hShift = $mix1 / 65535.0
    $hsv.H = ($hsv.H + 30 + ($hShift * 300)) % 360
    # Sat tweak: 0.7-1.3x
    $mix2 = ($sl -bxor ($cl * 1313)) -band 0xFFFFL
    $sFactor = 0.7 + ($mix2 / 65535.0) * 0.6
    $hsv.S = [Math]::Min(1.0, $hsv.S * $sFactor)
    # Value tweak: 0.85-1.15x
    $mix3 = ($sl -bxor ($cl * 7919)) -band 0xFFFFL
    $vFactor = 0.85 + ($mix3 / 65535.0) * 0.30
    $hsv.V = [Math]::Min(1.0, [Math]::Max(0.05, $hsv.V * $vFactor))
    return ConvertFrom-HSV $hsv
}

function Patch-Biome([string]$path, [string]$biomeName) {
    $seed = Get-BiomeSeed $biomeName
    $json = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json -AsHashtable

    # --- effects (foliage, grass, water) ---
    if ($json.ContainsKey('effects')) {
        $effects = $json['effects']
        foreach ($key in @('foliage_color','grass_color','water_color','water_fog_color','fog_color','sky_color')) {
            if ($effects.ContainsKey($key)) {
                $orig = [long]$effects[$key]
                $effects[$key] = Shift-Color $orig $seed ($key.GetHashCode())
            }
        }
    }

    # --- attributes (visual sky/fog/water_fog) MC 26.1 format ---
    if ($json.ContainsKey('attributes')) {
        foreach ($k in @('minecraft:visual/sky_color','minecraft:visual/fog_color','minecraft:visual/water_fog_color')) {
            if ($json['attributes'].ContainsKey($k)) {
                $orig = [long]$json['attributes'][$k]
                $json['attributes'][$k] = Shift-Color $orig $seed ($k.GetHashCode())
            }
        }
    }

    # --- climate: gentle shift in temperature & downfall ---
    if ($json.ContainsKey('temperature')) {
        $tShift = (((([long]$seed) -bxor 1234567L) -band 0xFFFFL) / 65535.0 - 0.5) * 0.4
        $json['temperature'] = [Math]::Round(([double]$json['temperature']) + $tShift, 3)
    }
    if ($json.ContainsKey('downfall')) {
        $dShift = (((([long]$seed) -bxor 7654321L) -band 0xFFFFL) / 65535.0 - 0.5) * 0.3
        $newD = [Math]::Round(([double]$json['downfall']) + $dShift, 3)
        $json['downfall'] = [Math]::Max(0.0, [Math]::Min(1.0, $newD))
    }

    return $json
}

# ----- Main pass -----

$biomeDir = Join-Path $dst "data\terralith\worldgen\biome"
if (-not (Test-Path $biomeDir)) { throw "Biome dir absent: $biomeDir" }

$biomeFiles = Get-ChildItem -LiteralPath $biomeDir -Filter "*.json" -File
Write-Host "[2/6] Patching $($biomeFiles.Count) Terralith biomes (color/climate shift)" -ForegroundColor Cyan

$counter = 0
foreach ($f in $biomeFiles) {
    $name = "terralith:$($f.BaseName)"
    $patched = Patch-Biome $f.FullName $name
    $patched | ConvertTo-Json -Depth 100 -Compress:$false | Set-Content -LiteralPath $f.FullName -Encoding UTF8
    $counter++
    if (($counter % 10) -eq 0) { Write-Host "  ... $counter / $($biomeFiles.Count)" -ForegroundColor Gray }
}
Write-Host "  done: $counter biomes patched" -ForegroundColor Green

# Skylands feature override: add monster_room loot, ore boost, etc.
# (Phase 1b - injected directly into existing biomes, since we just dezipped)
Write-Host "[3/6] Skylands loot enrichment (4 biomes)" -ForegroundColor Cyan
$skylandsBiomes = @('skylands_spring','skylands_summer','skylands_autumn','skylands_winter')
foreach ($s in $skylandsBiomes) {
    $bf = Join-Path $biomeDir "$s.json"
    if (-not (Test-Path $bf)) { Write-Host "  skip $s (missing)" -ForegroundColor Yellow; continue }
    $j = Get-Content -Raw -LiteralPath $bf | ConvertFrom-Json -AsHashtable
    if (-not $j.ContainsKey('features')) { continue }
    # Step 4 (UNDERGROUND_STRUCTURES) — add monster rooms
    if ($j['features'].Count -ge 4) {
        $list = [System.Collections.ArrayList]@($j['features'][3])
        if (-not $list.Contains('minecraft:monster_room')) { $null = $list.Add('minecraft:monster_room') }
        if (-not $list.Contains('minecraft:monster_room_deep')) { $null = $list.Add('minecraft:monster_room_deep') }
        $j['features'][3] = $list
    }
    # Step 6 (UNDERGROUND_ORES) — boost rare ores by adding extra placements
    if ($j['features'].Count -ge 7) {
        $list = [System.Collections.ArrayList]@($j['features'][6])
        if (-not $list.Contains('minecraft:ore_emerald')) { $null = $list.Add('minecraft:ore_emerald') }
        if (-not $list.Contains('minecraft:ore_diamond_buried')) { $null = $list.Add('minecraft:ore_diamond_buried') }
        $j['features'][6] = $list
    }
    $j | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $bf -Encoding UTF8
    Write-Host "  enriched $s" -ForegroundColor Gray
}

# License + patrons.txt cleanup: keep license.txt verbatim (compliance), regen pack.mcmeta marker
Write-Host "[4/6] pack.mcmeta marker (PTR v2)" -ForegroundColor Cyan
$mcmetaPath = Join-Path $dst "pack.mcmeta"
$mcmeta = @{
    pack = @{
        id = "terralith"
        min_format = 64
        max_format = 999
        description = @(
            @{ text = "Terralith"; color = "#1eb2bc" },
            @{ text = " - PTR v2 fork (private, non-redistributed)`n"; color = "gray" },
            @{ text = "color shift + climate shift + Skylands loot"; color = "#683edd" }
        )
    }
}
$mcmeta | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $mcmetaPath -Encoding UTF8

Write-Host "[5/6] Verify license.txt preserved (Stardust Labs license compliance)" -ForegroundColor Cyan
$lic = Join-Path $dst "license.txt"
if (Test-Path $lic) {
    $size = (Get-Item $lic).Length
    Write-Host "  license.txt present ($size bytes)" -ForegroundColor Gray
} else {
    Write-Host "  WARN: license.txt missing in extracted pack" -ForegroundColor Yellow
}

Write-Host "[6/6] Done. terralith_v2 patched at $dst" -ForegroundColor Green
