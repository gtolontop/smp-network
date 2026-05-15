# PTR backend — Folia 26.1.2 (V3 foundation)

This directory hosts the **Public Test Realm** server backend. Historical
Fabric/Polymer and Paper PTR snapshots were removed from the active repo; use
git history if you need to inspect those experiments.

V3 swaps that out for **Folia** plus **SMP Creation Kit**, a single in-house
base plugin built from `plugins/ptr-foundation/`. Trade-offs are documented in
[`../docs/PTR_V3_ARCHITECTURE.md`](../docs/PTR_V3_ARCHITECTURE.md).

## Binary

| Field | Value |
|---|---|
| Server | Folia |
| Minecraft | 26.1.2 |
| Build | `folia-26.1.2-8.jar` (channel: STABLE, released 2026-05-06) |
| Download | https://fill-data.papermc.io/v1/objects/607afd1c3320008e1ffd2eaee6780ace4419d5f8c527b75e79f259be79ebf57b/folia-26.1.2-8.jar |
| SHA256 | `607afd1c3320008e1ffd2eaee6780ace4419d5f8c527b75e79f259be79ebf57b` |
| Size | 53,184,326 bytes |
| Java | 25 — bundled at `../java/jdk-25.0.2+10/` |

Verify the file on first boot:

```powershell
(Get-FileHash .\folia-26.1.2-8.jar -Algorithm SHA256).Hash.ToLower()
# expected: 607afd1c3320008e1ffd2eaee6780ace4419d5f8c527b75e79f259be79ebf57b
```

The jar itself is not tracked by git (`*.jar` is gitignored). Re-download from
the URL above if you wipe the directory.

## Launch

From the repo root:

```powershell
.\scripts\start-ptr-folia.ps1     # PowerShell, foreground
# or
.\start-ptr-folia.bat             # cmd.exe wrapper
```

The Velocity proxy at `../velocity/` already routes the literal name `ptr`
to `127.0.0.1:25568`. Modern forwarding is wired through
`config/paper-global.yml -> proxies.velocity` (secret mirrors
`../velocity/forwarding.secret`).

## What ships in `config/`

| File | Source | Notes |
|---|---|---|
| `paper-global.yml` | Folia/Paper config | Sets `proxies.velocity.{enabled,online-mode,secret}`, disables note_block and mushroom neighbour updates for the disguise layer, names the timings server `PTR-Folia`. |
| `paper-world-defaults.yml` | Folia/Paper config | Anti-xray + entity tracking ranges + spawn limits for the isolated PTR backend. |

`folia-config.yml` lands in this directory on first boot — out of scope for
the foundation, leave it on defaults.

## Plugins

`plugins/` is gitignored. SMP Creation Kit is built from
`../plugins/ptr-foundation/` and lands at `plugins/SMPCreationKit-*.jar`. No other
plugin is expected on this backend at the creation-kit stage; in particular
core-paper / anticheat-paper / smp-logger stay on the lobby and survival
shards.

## Region threading note

Folia ticks distinct regions on distinct threads. The foundation plugin's
`SchedulerService` is the only sanctioned bridge to that model: never call
`Bukkit.getScheduler()` directly. See
[`../docs/FOLIA_PITFALLS.md`](../docs/FOLIA_PITFALLS.md).
