# Survival Server — Datapacks

## How Datapacks Work

Minecraft datapacks must be placed in the **world's datapack folder**, NOT in the
`plugins/` or `datapacks/` folder at the server root.

After the server boots for the first time and the `world` folder is generated, the
correct installation path is:

```
survival/
└── world/
    └── datapacks/       <-- place .zip files here
        ├── Terralith-2.x.x.zip
        ├── Tectonic-2.x.x.zip
        └── ...
```

> **Important:** Terrain-altering datapacks (Terralith, Tectonic) MUST be installed
> **before** the world generates any chunks — i.e., before the server runs for the
> first time, or in a freshly wiped world. Adding them to an existing world will
> cause visible terrain seams at chunk boundaries.

---

## Recommended Datapacks

### World Generation
| Datapack | Description | Download |
|---|---|---|
| **Terralith** | Overhauled overworld biomes; 100+ new biomes built on vanilla generation | https://modrinth.com/datapack/terralith |
| **Tectonic** | Dramatic terrain features — mountain ranges, valleys, cliffs | https://modrinth.com/datapack/tectonic |
| **Terratonic** | Compatibility layer between Terralith and Tectonic (required if using both) | https://modrinth.com/datapack/terratonic |
| **Nullscape** | Revamped End dimension | https://modrinth.com/datapack/nullscape |
| **Incendium** | Revamped Nether with new biomes and structures | https://modrinth.com/datapack/incendium |

### Structures
| Datapack | Description | Download |
|---|---|---|
| **Structory** | Atmospheric, lore-friendly new structures throughout the overworld | https://modrinth.com/datapack/structory |
| **Structory: Towers** | Adds more tower variants to complement Structory | https://modrinth.com/datapack/structory-towers |
| **Towns and Towers** | Additional villages and outposts that blend with Terralith biomes | https://modrinth.com/datapack/towns-and-towers |
| **YUNG's Better Structures** (Fabric mods, server-side) | Significantly improved dungeons, strongholds, ocean monuments, etc. — available as datapacks on Modrinth | https://modrinth.com/user/YUNGNICKYOUNG |

### Quality of Life
| Datapack | Description | Download |
|---|---|---|
| **Armor Statues** | GUI for precise armor-stand posing | https://vanillatweaks.net/picker/datapacks/ |
| **Multiplayer Sleep** | Only a percentage of players need to sleep to skip night | https://vanillatweaks.net/picker/datapacks/ |
| **Fast Leaf Decay** | Leaves decay faster after tree is chopped | https://vanillatweaks.net/picker/datapacks/ |
| **Graves** | Drops a grave on death; items are safe until retrieved | https://modrinth.com/datapack/graves |
| **Vanilla Tweaks** (bundle) | Large collection of small QoL tweaks — configure at vanillatweaks.net | https://vanillatweaks.net/picker/datapacks/ |

---

## Installation Steps

1. **Start the server once** (with an empty `world/` or a fresh server directory)
   to generate the `world/datapacks/` folder.  
   Then **stop the server immediately** if you are adding terrain datapacks.

2. Copy datapack `.zip` files into `world/datapacks/`.  
   Do **not** unzip them — Minecraft reads them as zips.

3. Start the server again. On first load with terrain datapacks, world generation
   will use the new biome data.

4. To verify datapacks are loaded, run in-game or console:
   ```
   /datapack list enabled
   ```

---

## Notes for Folia

- Folia uses regionized multithreading. Datapacks that add custom mobs or
  entities with complex AI may behave differently than on single-threaded Paper.
  Test in a staging world first.
- Structure datapacks are generally safe with Folia.
- Avoid datapacks that rely heavily on tick-based command execution
  (`/execute as @e[...]`) as these can interact poorly with region threading.
  Prefer plugin equivalents when available.

---

## World Border Reminder

This server uses a **10,000-block radius** (20,000 diameter) world border centered
at 0,0. Set it after first boot with:

```
/worldborder set 20000
/worldborder center 0 0
```

Pre-generate chunks within the border to avoid lag during exploration:
Use the **Chunky** plugin (`/chunky start`) for async, Folia-safe pre-generation.
