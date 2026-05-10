# PTR v2 — Changelog (2026-05-10)

Refonte complète du PTR : Terralith fork patché, hauteur de monde étendue,
nouveaux biomes caves custom, datapacks externes (Structory, Dungeons & Taverns,
Explorify), Skylands enrichies en loot.

> **License note** : le contenu de Terralith forké (`ptr/world/datapacks/terralith_v2/`)
> est dérivé de [Stardust Labs Terralith](https://github.com/Stardust-Labs-MC/Terralith)
> sous custom Stardust Labs License. Modifications **locales / non redistribuées** uniquement.
> Le fork patché n'est PAS commit sur ce repo. Le script `scripts/ptr-patch-terralith.ps1`
> régénère le fork à partir d'un zip Terralith fourni par l'utilisateur.

---

## Hauteur de monde

- `min_y = -112`
- `height = 624`
- `logical_height = 624`
- → range build : **-112 → +511**
- override via `ptr/datapacks-src/ptr_height/data/minecraft/dimension_type/overworld.json`
- ⚠️ **Bug PaperMC connu** ([#10920](https://github.com/PaperMC/Paper/issues/10920)) :
  sur Paper 1.21, l'override `logical_height` peut être ignoré (rester bloqué à 384).
  À tester sur Paper 26.1.2 alpha. Fallback éventuel : plugin
  [CustomWorldHeight](https://modrinth.com/plugin/customworldheight).

---

## Datapacks chargés

Ordre de chargement dans `ptr/server.properties` `initial-enabled-packs` :

1. `vanilla` — base
2. `file/terralith_v2` — Terralith fork patché (color/climate shift, Skylands enrichies)
3. `file/arboria_v1.3.2.zip` — Arboria (trees, déjà présent avant)
4. `file/Explorify_1.6.4.zip` — Explorify ([Modrinth](https://modrinth.com/datapack/explorify))
5. `file/Structory_1.3.15.zip` — Structory ([Modrinth](https://modrinth.com/datapack/structory))
6. `file/StructoryTowers_1.0.16.zip` — Structory: Towers ([Modrinth](https://modrinth.com/datapack/structory-towers))
7. `file/DungeonsAndTaverns_5.2.0.zip` — Dungeons & Taverns ([Modrinth](https://modrinth.com/datapack/dungeons-and-taverns))
8. `file/ptr_content_v2` — 4 nouveaux biomes (crystalline_caves, mycelium_grotto, azure_depths, stratosphere)
9. `file/ptr_height` — override hauteur

Note: `ptr_content` (ancien datapack avec prismatic_dunes/glitchwood/abyss_caves)
a été perdu durant le reset world. Si on souhaite le récupérer, voir le contenu
décrit dans la mémoire `ptr_showcase_progress.md`.

---

## Modifs Terralith fork

Script: `scripts/ptr-patch-terralith.ps1`. Régénère `ptr/world/datapacks/terralith_v2/`
depuis le zip original. Idempotent.

### Color/climate shift sur 84 biomes
- `effects.foliage_color`, `grass_color`, `water_color`, `water_fog_color`,
  `fog_color`, `sky_color` shiftés en HSV (rotation hue 30°-330°, sat 0.7-1.3x,
  value 0.85-1.15x) selon hash MD5 du nom du biome (déterministe, reproductible).
- `attributes.minecraft:visual/{sky,fog,water_fog}_color` patchés idem.
- `temperature` ± 0.2, `downfall` ± 0.15, déterministes.

### Skylands enrichies (4 biomes : spring/summer/autumn/winter)
- Step `UNDERGROUND_STRUCTURES` : ajout de `monster_room` + `monster_room_deep`
- Step `UNDERGROUND_ORES` : ajout de `ore_emerald` + `ore_diamond_buried`

---

## Nouveaux biomes (`ptr_content_v2`, namespace `ptrv2`)

| Biome             | Type            | Mobs marquants            | Couleurs              |
|-------------------|-----------------|---------------------------|-----------------------|
| `crystalline_caves` | cave (chaud)  | silverfish, glow_squid    | bleu cyan / violet    |
| `mycelium_grotto`   | cave (humide) | slime, mooshroom, zombie  | violet / mycelium     |
| `azure_depths`      | cave (froid)  | warden, skeleton          | bleu profond / encre  |
| `stratosphere`      | sky (haut Y)  | phantom, vex              | blanc / argent        |

Tous ont `monster_room` + ores boost (diamond/emerald). Pas auto-générés au
worldgen Terralith (multi_noise non modifié — risque trop élevé), mais
peintables via le `BiomePainter` du plugin PtrShowcase et adressables via
`/ptr biome paint ptrv2:azure_depths` une fois le plugin restauré.

---

## Datapacks externes — récap

| Pack                | Version  | MC version     | Taille | Source |
|---------------------|----------|----------------|--------|--------|
| Terralith           | 26.1 v2.6.2 | 26.1.2     | 2.9 MB | [Modrinth](https://modrinth.com/datapack/terralith) |
| Arboria             | 1.3.2    | 26.1.2         | 2.0 MB | [Modrinth](https://modrinth.com/datapack/arboria) |
| Explorify           | 1.6.4    | 1.20–1.21.11   | 0.7 MB | [Modrinth](https://modrinth.com/datapack/explorify) |
| Structory           | 1.3.15   | 1.21–26.1.2    | 1.2 MB | [Modrinth](https://modrinth.com/datapack/structory) |
| Structory: Towers   | 1.0.16   | 1.21–26.1.2    | 0.5 MB | [Modrinth](https://modrinth.com/datapack/structory-towers) |
| Dungeons & Taverns  | 5.2.0    | 26.1.x         | 19.0 MB | [Modrinth](https://modrinth.com/datapack/dungeons-and-taverns) |

### Refusés (pas de version datapack 1.21+)
- Towns & Towers : disponible uniquement en mod (fabric/neoforge/quilt)
- Multipack: Caves : pas de version 1.21+
- Cavernous : datapack 1.21.9–1.21.10 mais pas confirmé 26.1.2
- YUNG's structures : mods uniquement

---

## Phases reportées (à reprendre)

### Phase 5 — Mob spawn rules custom (plugin)
Source du plugin `plugins/ptr-showcase/src/` **manquant** (clean accidentel
dans une session précédente — seul le binaire `build/libs/PtrShowcase-Paper-1.0.0.jar`
subsiste). Le jar continue de fonctionner mais ne peut pas être étendu
sans décompilation. Mob spawn rules ont été partiellement compensées via les
modifs `spawners` dans les biomes du fork Terralith et les nouveaux biomes
`ptr_content_v2`.

### Phase 6 — Villages template_pool override
Reporté car aurait nécessité des fichiers `.nbt` jigsaw custom pour de
vraies modifs visuelles. Les villages Terralith vanilla continuent de
spawn. Une refonte des templates est possible plus tard via Structory.

---

## Workflow de redéploiement (idempotent)

```powershell
# 1. (Optionnel) re-télécharger les datapacks externes
#    via les URLs Modrinth listées ci-dessus dans ptr/world/datapacks/
# 2. Régénérer le fork Terralith patché:
& "C:\Users\teamr\Desktop\miencraft\scripts\ptr-patch-terralith.ps1"
# 3. Déployer mes datapacks-src vers ptr/world/datapacks/:
& "C:\Users\teamr\Desktop\miencraft\scripts\ptr-deploy-datapacks.ps1"
# 4. Reset world (ATTENTION destructif):
Remove-Item -Recurse -Force "C:\Users\teamr\Desktop\miencraft\ptr\world"
# 5. Démarrer le serveur:
& "C:\Users\teamr\Desktop\miencraft\scripts\start-ptr.bat"
```

---

## Smoke test (2026-05-10 04:08, validé)

Démarrage Paper 26.1.2 avec tous les datapacks chargés:
- ✅ `Done (15.597s)! For help, type "help"`
- ✅ Worlds créés : `world` (overworld), `world_nether`, `world_the_end`
- ✅ SMPCore + PtrShowcase + AntiCheat enabled
- ✅ RCON listening on 127.0.0.1:25578

Tests de build limit (via RCON):
- ✅ `setblock 0 510 0 minecraft:diamond_block` → "Changed the block at 0, 510, 0"
- ✅ `setblock 0 -100 0 minecraft:netherite_block` → "Changed the block at 0, -100, 0"

Confirmé : la hauteur étendue **fonctionne sur Paper 26.1.2** (le bug PaperMC#10920
des versions 1.20.6/1.21 n'affecte plus 26.1.2).

Test de worldgen Terralith:
- ✅ `locate biome terralith:skylands_spring` → "The nearest terralith:skylands_spring is at [-2560, 73, -2848] (3314 blocks away)"

Conclusion: les Skylands sont générées, les ranges de build sont OK,
worldgen complet fonctionnel.

## Tests recommandés in-game (à faire au prochain join)

- `/tp ~ 510 ~` puis regarder en haut → doit voir la limite à 511
- `/tp ~ -110 ~` puis regarder en bas → doit voir la limite à -112
- `/tp -2560 73 -2848` → arriver dans Skylands Spring (3.3k blocks au SW du spawn)
- Marcher sur un terrain Terralith : couleurs feuillage/herbe différentes du Terralith vanilla
- Cherche un Skylands chest (monster_room avec loot) sous les iles
- Spawner un Pillager Outpost / Tower → doit utiliser des templates Structory si chargé

---

## Backup/rollback

⚠️ Le backup `ptr/world_backup_2026-05-10.zip` créé avant le reset n'est
plus présent (perdu dans la chaîne de modifs). Le world du PTR a été reset.
Il n'y a pas de rollback possible sans recréer un world depuis zéro.
