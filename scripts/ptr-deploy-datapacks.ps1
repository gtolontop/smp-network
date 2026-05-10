# PTR v2 — deploy datapack sources from `ptr/datapacks-src/` to runtime `ptr/world/datapacks/`.
# Idempotent: replaces existing target dirs.
[CmdletBinding()]
param(
    [string]$PtrRoot = "C:\Users\teamr\Desktop\miencraft\ptr"
)
$ErrorActionPreference = 'Stop'

$srcRoot = Join-Path $PtrRoot "datapacks-src"
$dstRoot = Join-Path $PtrRoot "world\datapacks"

if (-not (Test-Path $srcRoot)) { throw "datapacks-src dir absent: $srcRoot" }
if (-not (Test-Path $dstRoot)) { New-Item -ItemType Directory -Path $dstRoot -Force | Out-Null }

$srcs = Get-ChildItem -LiteralPath $srcRoot -Directory
foreach ($s in $srcs) {
    $dst = Join-Path $dstRoot $s.Name
    if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
    Copy-Item -LiteralPath $s.FullName -Destination $dst -Recurse -Force
    Write-Host "Deployed: $($s.Name) -> $dst" -ForegroundColor Green
}
Write-Host "Done. $($srcs.Count) datapacks deployed." -ForegroundColor Cyan
