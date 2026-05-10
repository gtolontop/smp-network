# PTR V3 — notes d'inspiration (MythicMobs / ModelEngine / ItemsAdder)

> Issu de la lecture des `.jar` propriétaires fournis par l'utilisateur sous
> licence. Patterns architecturaux uniquement — réimplémentation propre dans
> notre style, pas de copie verbatim. Crédits dans
> [`ATTRIBUTIONS.md`](./ATTRIBUTIONS.md).

## Quoi a été lu

| Plugin | Version | Quoi exploré |
|---|---|---|
| **MythicMobs Premium** | 5.11.0-SNAPSHOT (Lumine) | `io.lumine.mythic.api.skills.*`, `mobs.*`, `drops.*`, `volatilecode.*` + configs par défaut + exemples |
| **ModelEngine** | R4.1.0 (Ticxo) | `com.ticxo.modelengine.api.animation.*`, `model.bone.*`, `generator.parser.blockbench.*`, `mount.*` |
| **ItemsAdder** | 4.0.16 (LoneDev) | `dev.lone.itemsadder.api.*` (CustomBlock, CustomEntity, CustomFurniture, CustomMob, Events) |

Tout dans `exemple/_decompiled/` (gitignored). 481 fichiers décompilés.

---

## Section 1 — MythicMobs : système skills/triggers/targeters

### Concepts retenus

| Concept | Rôle |
|---|---|
| `Skill` | Unité composable : trigger + condition* + targeter + mechanic+ |
| `SkillMechanic` | Atom : `damage`, `teleport`, `summon`, `effect:particles`, `effect:sound`, `potion`, `throw`, `message`, `speak`, `delay`, `blockmask`, ~80 builtins chez MM |
| `SkillTrigger` | `onCombat`, `onTimer:X`, `onDamaged`, `onPlayerKill`, `onDeath`, `onSpawn`, `onAttack`, `onSignal:Y` |
| `SkillCondition` | Filtre (`targetwithin{d=25}`, `healthbelow{p=0.5}`, `playersnearby{r=40}`) |
| `IEntityTargeter` / `ILocationTargeter` | Sélecteur (`@self`, `@target`, `@PlayersInRadius{r=40}`, `@LivingInRadius{r=10}`, `@trigger`) |
| `SkillMetadata` | Contexte runtime passé entre mécaniques (caster, trigger, targets, power, vars, parameters) |
| `IParentSkill` | Skill composé qui en appelle d'autres |
| `MythicMob` | Définition complète d'un mob : type, displayName, health, damage, equipment, **list of (trigger, skill, cooldown, threshold)**, faction, drops, AI selectors, bossbar |
| `DropTable` | Loot table avec **leaderboard hologramme de damage par player** — c'est ça qui rend les boss "shareables" |
| `ThreatTable` | Suit qui aggro le boss (différent du "qui est dans le radius") |
| `StatType` + `PlaceholderDouble` | Stats avec équations d'échelle (`V * ((1.05)^(L-1))`) |

### Exemple de mob (extrait de leur ExampleMobs.yml)

```yaml
SkeletonKing:
  Type: WITHER_SKELETON
  Display: '&6Skeleton King'
  Health: 500
  Damage: 10
  Skills:
  - speak{m="None may challenge!";cooldown=20} @PlayersInRadius{r=40} ~onCombat 0.2
  - skill{s=SummonSkeletons} @self 0.1
  - skill{s=SmashAttack} @target 0.2
  Equipment: [KingsCrown HEAD, SkeletonKingSword HAND]
  Drops: [SkeletonKingDrops]
```

Skill réutilisable :
```yaml
SmashAttack:
  Cooldown: 8
  Conditions: [targetwithin{d=25}]
  Skills:
  - message{m="Hahahah! I will crush you!"} @PlayersInRadius{r=40}
  - teleport @target
  - effect:sound{s=mob.endermen.portal;volume=1.0;pitch=0.5}
  - delay 10
  - damage{amount=5;ignorearmor=true} @PlayersInRadius{r=5}
  - throw{velocity=10;velocityY=5} @PlayersInRadius{r=5}
  - effect:explosion @Self
```

### Mapping vers `ptr-foundation`

Le `BossDefinition` / `BossPhase` / `PhaseController` actuels n'ont qu'un `Consumer<LivingEntity>` `onTick` / `onEnter`. **À remplacer/augmenter** par :

```
fr.smp.ptr.foundation.skill/
├── PtrSkill.java                  — composable skill
├── PtrSkillMechanic.java          — interface (one operation)
├── PtrSkillTrigger.java           — enum + custom event registry
├── PtrSkillTargeter.java          — interface (returns Collection<Entity|Location>)
├── PtrSkillCondition.java         — interface (boolean check)
├── PtrSkillContext.java           — équivalent SkillMetadata
├── PtrSkillRegistry.java          — registre des skills nommés
├── mechanic/
│   ├── DamageMechanic, TeleportMechanic, SummonMechanic,
│   │   ParticleMechanic, SoundMechanic, MessageMechanic, DelayMechanic,
│   │   ThrowMechanic, PotionMechanic, ExplosionMechanic
└── targeter/
    └── SelfTargeter, TargetTargeter, PlayersInRadiusTargeter, ...
```

Et dans `boss/` :
```java
BossDefinition.builder()
    .addSkill(SmashAttackSkill, SkillTrigger.onTimer(160))   // tous les 8s
    .addSkill(SummonSkill, SkillTrigger.onHpBelow(0.3))      // phase HP threshold
    .addSkill(SpeakSkill, SkillTrigger.onCombat())
```

**Différences volontaires avec MM** :
- Pas de DSL YAML inline (`@PlayersInRadius{r=40}`) à parser — on instancie en Java pour rester typé et région-aware
- Folia-first : chaque skill cast déclare son scheduler (region/entity/global/async) au lieu de tout faire sur main thread
- Pas de système de "power" multiplier opaque

---

## Section 2 — ModelEngine : animation 3D

### Concepts retenus

| Concept | Rôle |
|---|---|
| `BlueprintAnimation` | Animation issue du `.bbmodel` Blockbench (keyframes + interpolation) |
| `BlueprintAnimation.LoopMode` | `LOOP`, `ONCE`, `HOLD` |
| `BlueprintAnimation.OverrideMode` | Comment l'anim override les autres state-anims |
| `KeyframeType` + `KeyframeTypes` | Type de keyframe (rotation/translation/scale, script) |
| `KeyframeInterpolator` | Interpolation entre keyframes (`PrePostInterpolator`, `ScriptInterpolator`) |
| `ModelBone` | Un os, position + rotation + scale interpolable |
| `BoneBehaviorTypes` | Comportement attachable (head, hitbox, mount, leash) |
| `MountManager` + `MountData` | Bones-passagers (un bone peut porter une entity) |
| `LeashManager` | Leash points per-bone |
| `ActiveModel` | Instance live d'un modèle attaché à une `ModeledEntity` |
| `ModeledEntity` | Wrapper d'une Bukkit entity qui porte des modèles |
| `AnimationHandler` | Interface centrale (state machine) |
| `ModelState` | Enum d'états (`IDLE`, `WALK`, `STRAFE`, `JUMP_START`, `JUMP`, `JUMP_END`, `HOVER`, `FLY`, `SPAWN`, `DEATH`) |
| `BlockbenchDeserializer` (`v5_0` + Legacy) | Parser `.bbmodel` → `Blueprint` |
| `lerp_in / lerp_out / speed / merge` | Paramètres d'animation |

### Pattern principal (interface API)

```java
public interface AnimationHandler {
    ActiveModel getActiveModel();
    void tickGlobal();
    void updateBone(ModelBone bone);
    IAnimationProperty playAnimation(
        String name, double lerpIn, double lerpOut, double speed, boolean force);
    void stopAnimation(String name);
    boolean isPlayingAnimation(String name);
    void setDefaultProperty(DefaultProperty p);   // pour les états par défaut
}
```

Chaque `ModelBone` est rendu comme un `ItemDisplay` Bukkit, animé via `setTransformation(...)` chaque tick. Le `.bbmodel` est parsé en JSON au load et converti en `BlueprintAnimation` (séquence de keyframes par bone, par axe). L'`AnimationHandler` lerp entre keyframes selon le tick courant + speed multiplier.

### `folia-supported: true` ✅

Leur `plugin.yml` confirme — Folia ne pose pas de problème pour le pattern. Chaque modèle est entity-scoped donc on tick via `EntityScheduler`.

### Mapping vers `ptr-foundation`

Nouvelle couche **`model/`** (avant `boss/`, parce que boss l'utilise pour les visuels) :

```
fr.smp.ptr.foundation.model/
├── Blueprint.java                 — record immutable du modèle parsé
├── BlueprintBone.java             — record (name, parent, pivot, cubes[])
├── BlueprintAnimation.java        — record (name, loopMode, duration, keyframes par bone)
├── Keyframe.java                  — record (channel, time, value, interpolation)
├── ModelState.java                — enum (IDLE/WALK/JUMP/ATTACK/HURT/DEATH/SPAWN)
├── parser/
│   └── BlockbenchParser.java     — JSON .bbmodel → Blueprint
├── ActiveModel.java               — instance live (entity-bound)
├── ModelBoneInstance.java         — ItemDisplay rig d'un bone
├── animation/
│   ├── AnimationController.java   — state machine + lerp engine
│   ├── AnimationProperty.java     — (lerpIn, lerpOut, speed, currentTick)
│   └── Interpolators.java         — LINEAR, STEP, BEZIER, CATMULL_ROM
└── mount/
    └── MountController.java       — bones-as-mount handling
```

### Stratégie d'implémentation

ModelEngine fait BEAUCOUP de choses. Pour V3 on cible 80% du wow avec 20% du code :
- **MVP** : parser `.bbmodel` v5 (le format actuel Blockbench), state IDLE + WALK + ATTACK + DEATH, interpolation linéaire
- Plus tard : Bezier, mount bones, custom hitbox per bone, animation merge

---

## Section 3 — ItemsAdder : APIs publiques (DX)

### Concepts retenus

| Classe | Pattern |
|---|---|
| `CustomBlock` | Statique : `getInstance(id)`, `place(id, loc)`, `remove(loc)`, `byAlreadyPlaced(block)`, `byItemStack(stack)`. Instance : `getBlock()`, `getLoot()`, `playBreakEffect()`, `setCurrentLightLevel(int)` |
| `CustomEntity` | Statique : `spawn(id, loc)`, **`convert(id, livingEntity)`** ←— très utile, convertit un mob vanilla, `byAlreadySpawned(entity)`. Instance : `playAnimation(name, onFinish)`, `getBones()`, `getMountBones()`, `setColorAllBones(rgb)` |
| `CustomEntity.Bone` | Per-bone : `getName()`, `getLocation()`, `getColor()`, `setColor(rgb)`, `setEnchanted(bool)` |
| `CustomFurniture` | `spawn(id, block)`, `spawnPreciseNonSolid(id, loc)`, `replaceFurniture(id, color)`, `setColor(Color)` (potion/leather meta) |
| `CustomStack` | Base de tout — wrapper d'un `ItemStack` tagué |
| **Events** | `CustomBlockPlaceEvent`, `CustomBlockBreakEvent`, `CustomBlockInteractEvent`, `FurniturePlaceEvent` (place), `FurniturePlacedEvent` (post-place), `FurnitureBreakEvent`, `FurnitureInteractEvent`, `CustomEntityDeathEvent` |

### Le pattern qui rend l'API agréable

**Statique pour le lookup, instance pour les ops.** Exemple :

```java
// Au lieu de :
PtrBlockRegistry reg = services.get(PtrBlockRegistry.class);
PtrBlockDef def = reg.get(PtrIds.key("runesteel")).orElseThrow();
NoteBlockCarrier carrier = services.get(NoteBlockCarrier.class);
carrier.place(loc, new NoteBlockCarrier.State(...));

// Tu écris :
PtrBlock.place("runesteel", loc);
```

C'est ce que MM appelle "facade statique" + ce que la couche `disguise` actuelle ne fait pas. À ajouter.

### Convertir mob vanilla → custom

`CustomEntity.convert(id, existingMob)` transforme un mob déjà spawné. Très utile pour :
- World event qui rend les zombies "ptr:bone_zombie" autour d'un autel
- Boss summon qui transforme les vagues de mobs vanilla

### Per-bone state visuel

`setColor(rgb)` + `setEnchanted(bool)` per bone, déclenché depuis un skill. Permet des effets visuels riches sans nouveau modèle :
- Boss en colère → bones glow rouge
- Phase enraged → bones enchanted shimmer

### Mapping vers `ptr-foundation`

Nouvelles classes utility / facade :

```
fr.smp.ptr.foundation.api/
├── PtrBlocks.java                 — statique : place/remove/byAlreadyPlaced
├── PtrItems.java                  — statique : create(id), byItemStack(stack)
├── PtrEntities.java               — statique : spawn(id, loc), convert(id, mob)
├── PtrFurnitures.java             — statique : spawn/remove/replaceColor
└── events/                        — Bukkit events publics
    ├── PtrBlockPlaceEvent.java
    ├── PtrBlockBreakEvent.java
    ├── PtrBlockInteractEvent.java
    ├── PtrEntityDeathEvent.java
    ├── PtrFurniturePlaceEvent.java
    └── ...
```

Les façades wrappent les couches internes (`disguise`, `registry`, `storage`) pour offrir une API plate et facile à apprendre.

---

## Section 4 — Roadmap d'intégration proposée

Vu que la foundation actuelle est minimale, je propose 4 itérations dans cet ordre :

### Itération A — **Skill system** (la plus haute valeur)
- Couche `skill/` complète (interface + 10 mécaniques + 5 targeters + 3 conditions)
- Wire dans `BossDefinition.addSkill(skill, trigger)`
- `PhaseController` joue les skills par phase au lieu de `Consumer<LivingEntity>` opaque
- **Effort estimé** : 1 session ~2-3h
- **Résultat** : bosses scriptables comme MM mais en code Java typé

### Itération B — **API facades publiques** (DX immédiate)
- `PtrBlocks` / `PtrItems` / `PtrEntities` / `PtrFurnitures` statiques
- 6 events Bukkit (`PtrBlockPlaceEvent`, etc.)
- Wire dans les carriers existants pour fire les events
- **Effort estimé** : 1 session ~1-2h
- **Résultat** : le content layer écrit `PtrBlocks.place("runesteel", loc)` sans naviguer 4 services

### Itération C — **Resource pack pipeline**
- `src/main/resources/pack/` dans `ptr-foundation`
- Gradle task `buildResourcePack` qui zip + sha1
- Auto-host via `dist/ptr-resourcepack.zip` (réutiliser le flow GitHub-raw existant)
- `/ptrf pack reload` qui force le client reload (commande iatexture-style)
- **Effort estimé** : 1 session ~2h
- **Résultat** : les futurs content layers déposent des assets dans `pack/`, ça se zippe au build

### Itération D — **Model engine (3D animation)**
- Parser `.bbmodel` (v5_0 format actuel)
- `Blueprint` + `BlueprintAnimation` + `ModelState` enum
- `ActiveModel` + `AnimationController` (state machine + lerp linéaire)
- `ModelBoneInstance` (ItemDisplay rig)
- Update `DisplayMobCarrier` pour utiliser `ActiveModel`
- **Effort estimé** : 2-3 sessions
- **Résultat** : `DisplayMobCarrier` peut jouer des animations Blockbench

### Itération E (optionnelle) — **Drop leaderboard**
- `DropTable` avec damage tracking par player
- Hologramme leaderboard à la mort du boss
- **Effort estimé** : 1 session
- **Résultat** : ce qui rend les boss publics "instagrammables"

---

## Section 5 — Crédits

Voir [`ATTRIBUTIONS.md`](./ATTRIBUTIONS.md). Les patterns architecturaux ci-dessus sont inspirés de :
- **MythicMobs Premium** par Lumine (mythiccraft.io)
- **ModelEngine R4** par Ticxo (mcmodelengine.com)
- **ItemsAdder** par LoneDev (itemsadder.devs.beer)

Aucune ligne de leur code n'est intégrée verbatim — la réimplémentation reprend uniquement les concepts publics d'organisation (interfaces, énumérations d'états, noms de méthodes idiomatiques).
