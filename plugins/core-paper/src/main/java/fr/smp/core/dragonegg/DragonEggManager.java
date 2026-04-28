package fr.smp.core.dragonegg;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Suit l'unique œuf du dragon "tracké" du serveur :
 *   - Pose / casse / tenu / dropé / brûlé / void / explosion → tracé en chat.
 *   - Quand il est posé ou au sol : faisceau vertical de particules + anneau qui tourne.
 *   - Tenu en inventaire : 2.5 ❤ d'absorption + effets balise pour le porteur ET sa team, glow visible.
 *   - Stockage en coffre / hopper / dispenser : refusé (le listener gère ça).
 *   - Joueur se déco avec l'œuf → l'œuf se "cristallise" en bloc à ses pieds avec le faisceau classe.
 *   - Détruit (feu / lave / void / explosion) → broadcast détruit, compte à rebours, respawn auto à
 *     l'autel de respawn (configurable, par défaut sur la fontaine du End).
 *
 * État persisté dans plugins/SMPCore/dragonegg-state.yml — survit aux restart pendant le countdown.
 */
public final class DragonEggManager {

    public enum State { LOST, PRESENT, DESTROYED }

    private static final long DEFAULT_RESPAWN_SECONDS = 90L;
    private static final long DEFAULT_RECLAIM_SECONDS = 3600L;
    private static final long DEFAULT_PLACE_COOLDOWN_SECONDS = 60L;
    private static final long TICK_PERIOD = 5L;                    // 4 Hz
    private static final long TRACKER_UPDATE_INTERVAL_TICKS = 20L;
    private static final long LOST_RESPAWN_RETRY_MS = 30_000L;       // tente le respawn auto depuis LOST toutes les 30s
    private static final double BEACON_RANGE = 64.0;                // applies to placed egg
    private static final int ALTAR_RADIUS = 2;                      // 5x5 footprint
    private static final int[][] CORNERS = { {-1, -1}, {-1, +1}, {+1, -1}, {+1, +1} };
    private static final long LIGHTNING_MIN_TICKS = 60L;             // 3s
    private static final long LIGHTNING_MAX_TICKS = 180L;            // 9s
    private static final long PLACEMENT_CONFIRM_MS = 30_000L;

    private static final PotionEffectType[] BEACON_EFFECTS = new PotionEffectType[] {
            PotionEffectType.SPEED,
            PotionEffectType.HASTE,
            PotionEffectType.RESISTANCE,
            PotionEffectType.JUMP_BOOST,
            PotionEffectType.STRENGTH,
            PotionEffectType.REGENERATION
    };

    private final SMPCore plugin;
    private final DragonEggItem item;
    private final File stateFile;

    // ---- Persisted state ----
    private State state = State.LOST;
    private Location respawnLocation;        // base autel respawn (configurable)
    private Location lastKnownLocation;
    private long respawnAt = 0L;             // epoch ms; > 0 only when DESTROYED
    private long placedReclaimAt = 0L;       // epoch ms; > 0 while a player-placed egg is locked
    private UUID lastHolder;

    // ---- Runtime state (rebuilt each tick from world / events) ----
    private Location placedAt;
    private UUID itemEntityUuid;
    private UUID currentHolder;

    // ---- Tasks & throttles ----
    private BukkitTask tickTask;
    private long lastBroadcastSecondsLeft = -1L;
    private long lastTrackerUpdateTicks = 0L;
    private long ringPhaseTicks = 0L;
    private long lastLostRespawnAttemptMs = 0L;
    private long nextLightningTicks = -1L;
    private final Random random = new Random();
    private final Map<UUID, PendingPlacement> pendingPlacements = new HashMap<>();
    private final Map<UUID, Long> placementCooldowns = new HashMap<>();
    private final Set<UUID> forcedGlowingHolders = new HashSet<>();

    // ---- Altar structure backup (placement -> originalBlockData). LinkedHashMap pour rejouer les
    // restaurations dans l'ordre inverse de la pose et éviter les soucis de physics. ----
    private final Map<Location, BlockData> altarBackup = new LinkedHashMap<>();

    private record PendingPlacement(Location location, long expiresAt) {}

    public DragonEggManager(SMPCore plugin) {
        this.plugin = plugin;
        this.item = new DragonEggItem(plugin);
        this.stateFile = new File(plugin.getDataFolder(), "dragonegg-state.yml");
    }

    public DragonEggItem item() { return item; }

    public void start() {
        plugin.getDataFolder().mkdirs();
        loadState();
        ensureDefaultRespawnLocation();

        // Si on était en countdown au moment du shutdown et que le respawn est passé, on respawn maintenant.
        // Délai 40 ticks pour laisser les mondes finir de charger.
        if (state == State.DESTROYED && respawnAt > 0L && System.currentTimeMillis() >= respawnAt) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> executeRespawn(false), 40L);
        }

        // Dedupe à froid une fois les mondes chargés. 60 ticks = 3s pour laisser les players join.
        Bukkit.getScheduler().runTaskLater(plugin, () -> deduplicate("boot"), 60L);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK_PERIOD);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        clearStaleForcedGlow(null);
        saveState();
    }

    // =======================================================================
    //  Public state accessors
    // =======================================================================

    public State currentState() { return state; }
    public Location respawnLocation() { return respawnLocation == null ? null : respawnLocation.clone(); }
    public Location lastKnownLocation() { return lastKnownLocation == null ? null : lastKnownLocation.clone(); }
    public UUID currentHolder() { return currentHolder; }
    public UUID lastHolder() { return lastHolder; }
    public Location placedAt() { return placedAt == null ? null : placedAt.clone(); }

    public long secondsUntilRespawn() {
        if (state != State.DESTROYED || respawnAt <= 0L) return -1L;
        return Math.max(0L, (respawnAt - System.currentTimeMillis()) / 1000L);
    }

    public long respawnSeconds() {
        return plugin.getConfig().getLong("dragonegg.respawn-seconds", DEFAULT_RESPAWN_SECONDS);
    }

    public long reclaimSeconds() {
        return plugin.getConfig().getLong("dragonegg.reclaim-seconds", DEFAULT_RECLAIM_SECONDS);
    }

    public long placeCooldownSeconds() {
        return plugin.getConfig().getLong("dragonegg.place-cooldown-seconds", DEFAULT_PLACE_COOLDOWN_SECONDS);
    }

    public long secondsUntilReclaim() {
        if (placedAt == null || placedReclaimAt <= 0L) return 0L;
        return Math.max(0L, (long) Math.ceil((placedReclaimAt - System.currentTimeMillis()) / 1000.0));
    }

    public long secondsUntilPlacementCooldown(Player player) {
        if (player == null) return 0L;
        Long until = placementCooldowns.get(player.getUniqueId());
        if (until == null) return 0L;

        long secondsLeft = Math.max(0L, (long) Math.ceil((until - System.currentTimeMillis()) / 1000.0));
        if (secondsLeft <= 0L) {
            placementCooldowns.remove(player.getUniqueId());
        }
        return secondsLeft;
    }

    public boolean canPlaceEgg(Player player) {
        long secondsLeft = secondsUntilPlacementCooldown(player);
        if (secondsLeft <= 0L) return true;
        if (player != null) {
            player.sendMessage(Msg.err("Attends encore <yellow>" + Msg.duration(secondsLeft) + "</yellow> avant de reposer l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient>."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.2f);
        }
        return false;
    }

    public boolean setRespawnLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        respawnLocation = loc.clone();
        saveState();
        return true;
    }

    public ItemStack createEgg() { return item.build(); }

    public ItemStack createTracker() { return item.buildTracker(); }

    public int giveNewEgg(Player target, String reason) {
        if (target == null) return 0;
        int removed = purgeAll(reason);

        ItemStack egg = createEgg();
        var overflow = target.getInventory().addItem(egg);
        if (overflow.isEmpty()) {
            markHeldBy(target);
        } else {
            for (ItemStack stack : overflow.values()) {
                Item drop = target.getWorld().dropItemNaturally(target.getLocation(), stack);
                markDropped(drop);
            }
        }
        saveState();
        return removed;
    }

    public void giveTracker(Player target) {
        if (target == null) return;
        ItemStack tracker = createTracker();
        var overflow = target.getInventory().addItem(tracker);
        if (!overflow.isEmpty()) {
            for (ItemStack stack : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), stack);
            }
        }
    }

    /**
     * Purge tous les œufs (inv online + drops + bloc posé), retire l'autel, reset l'état → LOST.
     * Utilisé par /dragonegg give pour garantir une instance unique avant d'en redonner une.
     */
    public int purgeAll(String reason) {
        int removed = purgeVisibleEggItems();
        removed += removePlacedEgg(true);
        tearDownAltar(true);
        state = State.LOST;
        respawnAt = 0L;
        placedReclaimAt = 0L;
        currentHolder = null;
        itemEntityUuid = null;
        placedAt = null;
        nextLightningTicks = -1L;
        plugin.getLogger().info("DragonEgg: purgeAll (" + reason + ") removed " + removed + " egg(s)");
        saveState();
        return removed;
    }

    public int countTrackedEggs() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack s : p.getInventory().getContents()) {
                if (item.isTracked(s)) n++;
            }
        }
        for (World w : Bukkit.getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) {
                if (!it.isDead() && item.isTracked(it.getItemStack())) n++;
            }
        }
        if (placedAt != null
                && placedAt.getWorld() != null
                && placedAt.getWorld().isChunkLoaded(placedAt.getBlockX() >> 4, placedAt.getBlockZ() >> 4)
                && placedAt.getBlock().getType() == Material.DRAGON_EGG) n++;
        return n;
    }

    public boolean isTracked(ItemStack stack) { return item.isTracked(stack); }

    public boolean isTrackedBlock(Block block) {
        // Un bloc DRAGON_EGG sur lequel notre placedAt pointe est considéré tracké.
        if (block == null || block.getType() != Material.DRAGON_EGG) return false;
        if (placedAt == null) return false;
        return sameBlock(block.getLocation(), placedAt);
    }

    public boolean isAltarBlock(Block block) {
        if (block == null || altarBackup.isEmpty()) return false;
        return altarBackup.containsKey(block.getLocation());
    }

    public boolean isBeaconBeamColumn(Block block) {
        if (block == null || placedAt == null || placedAt.getWorld() == null) return false;
        if (!block.getWorld().equals(placedAt.getWorld())) return false;
        int dx = block.getX() - placedAt.getBlockX();
        int dz = block.getZ() - placedAt.getBlockZ();
        boolean beaconColumn = false;
        for (int[] c : CORNERS) {
            if (dx == c[0] && dz == c[1]) {
                beaconColumn = true;
                break;
            }
        }
        return beaconColumn && block.getY() >= placedAt.getBlockY() + 1;
    }

    public boolean confirmPlacement(Player player, Block block) {
        if (player == null || block == null) return false;
        if (!canPlaceEgg(player)) {
            pendingPlacements.remove(player.getUniqueId());
            return false;
        }

        UUID uuid = player.getUniqueId();
        Location location = block.getLocation();
        long now = System.currentTimeMillis();
        PendingPlacement pending = pendingPlacements.get(uuid);
        if (pending != null && pending.expiresAt() >= now && sameBlock(pending.location(), location)) {
            pendingPlacements.remove(uuid);
            return true;
        }

        pendingPlacements.put(uuid, new PendingPlacement(location, now + PLACEMENT_CONFIRM_MS));
        player.sendMessage(Msg.info("<yellow>Confirmation requise :</yellow> <gray>l'autel va remplacer temporairement les blocs dans une zone <white>5x5</white> autour de l'œuf.</gray>"));
        player.sendMessage(Msg.info("<gray>L'œuf reste dans ton inventaire. Repose-le au même endroit dans <yellow>30s</yellow> pour confirmer.</gray>"));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.7f);
        return false;
    }

    private void startPlacementCooldown(Player player) {
        if (player == null) return;
        long seconds = placeCooldownSeconds();
        if (seconds <= 0L) return;
        placementCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    // =======================================================================
    //  Event hooks (appelés par DragonEggListener)
    // =======================================================================

    public void onItemPickup(Player player) {
        markHeldBy(player);
        player.sendMessage(Msg.ok("Tu as récupéré l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient>."));
        deduplicate("pickup");
        saveState();
    }

    public void onItemDrop(Player player, Item entity) {
        clearForcedGlow(player.getUniqueId());
        markDropped(entity);
        saveState();
    }

    public void onItemSpawn(Item entity) {
        // Item naturel (depuis BlockBreak / explosion / etc.) : on l'enregistre.
        markDropped(entity);
        deduplicate("spawn");
    }

    public void onBlockPlace(Player player, Block block) {
        if (takeFromInventory(player) == null) {
            player.sendMessage(Msg.err("Impossible de poser l'œuf : item introuvable dans ton inventaire."));
            return;
        }

        clearForcedGlow(player.getUniqueId());
        currentHolder = null;
        lastHolder = player.getUniqueId();
        placedAt = block.getLocation();
        lastKnownLocation = placedAt;
        itemEntityUuid = null;
        state = State.PRESENT;
        placedReclaimAt = System.currentTimeMillis() + reclaimSeconds() * 1000L;

        buildAltar(placedAt);
        block.setType(Material.DRAGON_EGG, false);
        player.sendMessage(Msg.ok("Œuf posé. Il sera récupérable dans <yellow>" + Msg.duration(reclaimSeconds()) + "</yellow>."));
        playPlacementIntro(placedAt);
        playPlacedLightningBurst(placedAt.toCenterLocation(), 14, 10.0);
        startPlacementCooldown(player);
        scheduleNextLightning();
        deduplicate("pose");
        saveState();
    }

    public void onBlockBreak(Player player, Block block) {
        if (placedAt != null && sameBlock(block.getLocation(), placedAt)) {
            playRemovalOutro(placedAt);
            tearDownAltar();
            placedAt = null;
            placedReclaimAt = 0L;
            nextLightningTicks = -1L;
            startPlacementCooldown(player);
            saveState();
        }
    }

    public void onBlockBreak(Block block) {
        onBlockBreak(null, block);
    }

    public boolean canReclaimPlacedEgg(Player player) {
        long secondsLeft = secondsUntilReclaim();
        if (secondsLeft <= 0L) return true;
        if (player != null) {
            player.sendMessage(Msg.err("L'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient> est verrouillé encore <yellow>" + Msg.duration(secondsLeft) + "</yellow>."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.2f);
        }
        return false;
    }

    /** Le bloc DRAGON_EGG est tombé (FallingBlock) puis a re-atterri à newLoc. */
    public void onBlockFall(Block oldBlock, Location newLoc) {
        if (placedAt == null || !sameBlock(oldBlock.getLocation(), placedAt)) return;
        placedAt = newLoc;
        lastKnownLocation = newLoc;
        saveState();
    }

    /**
     * Le joueur quitte (ou est transféré) avec l'œuf. On le retire de son inventaire et on le pose
     * comme bloc au sol à ses pieds avec le gros faisceau. Le joueur ne pourra pas l'emporter ailleurs.
     */
    public void onHolderQuit(Player player) {
        ItemStack egg = takeFromInventory(player);
        if (egg == null) return;
        clearForcedGlow(player.getUniqueId());

        Location feet = player.getLocation();
        Location dropTarget = findGroundFor(feet);
        if (dropTarget == null) {
            // Aucun spot trouvé → fallback : pose à la respawn location, plutôt que de perdre l'œuf.
            dropTarget = (respawnLocation != null) ? respawnLocation.clone() : feet.clone();
        }
        Block target = dropTarget.getBlock();
        placedAt = target.getLocation();
        currentHolder = null;
        lastKnownLocation = placedAt;
        if (state != State.PRESENT) state = State.PRESENT;
        placedReclaimAt = System.currentTimeMillis() + reclaimSeconds() * 1000L;
        buildAltar(placedAt);
        target.setType(Material.DRAGON_EGG, false);
        playPlacementIntro(placedAt);
        playPlacedLightningBurst(placedAt.toCenterLocation(), 12, 8.0);
        scheduleNextLightning();
        saveState();
    }

    /**
     * Le porteur change de serveur (Velocity transfer). Identique au quit pour notre logique.
     */
    public void onHolderTransfer(Player player) {
        onHolderQuit(player);
    }

    /**
     * L'œuf est détruit (feu / lave / void / explosion / cause admin). On lance le countdown.
     */
    public void destroy(String reason, Location at) {
        if (state == State.DESTROYED) return;
        purgeVisibleEggItems();
        removePlacedEgg(true);
        tearDownAltar(true);
        state = State.DESTROYED;
        respawnAt = System.currentTimeMillis() + respawnSeconds() * 1000L;
        lastKnownLocation = (at != null) ? at.clone() : lastKnownLocation;
        currentHolder = null;
        itemEntityUuid = null;
        placedAt = null;
        placedReclaimAt = 0L;
        nextLightningTicks = -1L;
        lastBroadcastSecondsLeft = -1L;

        if (at != null && at.getWorld() != null) {
            at.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.7f, 1.6f);
            at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 3, 0.4, 0.4, 0.4, 0);
            at.getWorld().spawnParticle(Particle.DRAGON_BREATH, at, 200, 1.0, 1.0, 1.0, 0.1, 1.0f);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
        }

        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<dark_red>✦</dark_red> <red><bold>L'Œuf du Dragon a été détruit !</bold></red> <gray>(" + reason + ")</gray>"));
        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<gray>Respawn à l'autel dans <yellow>" + respawnSeconds() + "s</yellow>."));
        saveState();
    }

    /** Force un respawn immédiat à la respawn location (admin). */
    public void respawnNow() {
        executeRespawn(true);
    }

    // =======================================================================
    //  Tick loop
    // =======================================================================

    private void tick() {
        ringPhaseTicks += TICK_PERIOD;
        long now = System.currentTimeMillis();
        boolean updateTrackers = ringPhaseTicks - lastTrackerUpdateTicks >= TRACKER_UPDATE_INTERVAL_TICKS;

        if (placedAt == null && !altarBackup.isEmpty()) {
            tearDownAltar(false);
        }

        // Belt-and-suspenders: apply holder effects to anyone with the tracked egg in inv,
        // independent of state. The state machine below also handles this, but routes can
        // miss it (state stuck in DESTROYED/LOST during a tick after adoption, stale
        // placedAt/itemEntityUuid blocking the find) — this guarantees the buff applies
        // every tick the egg is actually in someone's inventory.
        Player liveHolder = findOnlineEggHolder();
        if (liveHolder != null) {
            applyHolderEffects(liveHolder);
            liveHolder.setGlowing(true);
            forcedGlowingHolders.add(liveHolder.getUniqueId());
            applyTeamEffects(liveHolder);
        }

        if (state == State.DESTROYED) {
            if (adoptVisibleTrackedEgg("destroyed")) {
                return;
            }
            if (updateTrackers) {
                updateTrackerItems(null);
                lastTrackerUpdateTicks = ringPhaseTicks;
            }
            long secondsLeft = Math.max(0L, (respawnAt - now) / 1000L);
            if (secondsLeft <= 0L) {
                executeRespawn(false);
                return;
            }
            announceCountdown(secondsLeft);
            return;
        }

        if (state == State.LOST) {
            if (adoptVisibleTrackedEgg("lost")) {
                return;
            }
            if (updateTrackers) {
                updateTrackerItems(null);
                lastTrackerUpdateTicks = ringPhaseTicks;
            }
            // Auto-recovery : si l'œuf est marqué perdu (premier boot, ou respawn échoué parce que
            // les mondes n'étaient pas prêts), on retente périodiquement dès que l'autel est valide.
            if (now - lastLostRespawnAttemptMs >= LOST_RESPAWN_RETRY_MS) {
                lastLostRespawnAttemptMs = now;
                if (respawnLocation == null || respawnLocation.getWorld() == null) {
                    ensureDefaultRespawnLocation();
                }
                if (respawnLocation != null && respawnLocation.getWorld() != null) {
                    executeRespawn(false);
                }
            }
            return;
        }

        if (state != State.PRESENT) return;

        // Resync runtime tracking from world state.
        Player holder = (currentHolder != null) ? Bukkit.getPlayer(currentHolder) : null;
        if (holder != null && findEggInInventory(holder) == null) {
            clearForcedGlow(holder.getUniqueId());
            currentHolder = null;
            holder = null;
        }

        boolean placedOk = placedAt != null
                && placedAt.getWorld() != null
                && placedAt.getWorld().isChunkLoaded(placedAt.getBlockX() >> 4, placedAt.getBlockZ() >> 4)
                && placedAt.getBlock().getType() == Material.DRAGON_EGG;
        if (placedAt != null && !placedOk) {
            // Le chunk peut être déchargé : on garde l'info, mais on ne dessine pas.
            // Si le chunk est chargé et que le bloc n'est plus là, on perd la trace.
            if (placedAt.getWorld() != null
                    && placedAt.getWorld().isChunkLoaded(placedAt.getBlockX() >> 4, placedAt.getBlockZ() >> 4)) {
                tearDownAltar();
                placedAt = null;
                placedReclaimAt = 0L;
            }
        }
        // Re-check after potential clearing.
        placedOk = placedAt != null
                && placedAt.getWorld() != null
                && placedAt.getWorld().isChunkLoaded(placedAt.getBlockX() >> 4, placedAt.getBlockZ() >> 4)
                && placedAt.getBlock().getType() == Material.DRAGON_EGG;

        Item itemEnt = (itemEntityUuid != null) ? findItemEntity(itemEntityUuid) : null;
        if (itemEntityUuid != null && itemEnt == null) {
            // L'entity item a disparu (despawn / merge / pickup non capturé).
            // On ne déclenche pas destroy ici — c'est l'event de combust/void qui doit le faire.
            itemEntityUuid = null;
        }

        if (holder == null && !placedOk && itemEnt == null) {
            holder = findOnlineEggHolder();
            if (holder != null) {
                markHeldBy(holder);
            }
        }

        clearStaleForcedGlow(holder == null ? null : holder.getUniqueId());

        if (holder != null) {
            applyHolderEffects(holder);
            holder.setGlowing(true);
            forcedGlowingHolders.add(holder.getUniqueId());
            renderHolderParticles(holder);
            applyTeamEffects(holder);
            lastKnownLocation = holder.getLocation();
        }

        if (placedOk) {
            if ((ringPhaseTicks % 20L) == 0L) {
                maintainBeaconBeams(placedAt);
            }
            renderBeam(placedAt.toCenterLocation());
            renderRing(placedAt.toCenterLocation());
            renderAltarAura(placedAt);
            applyPlacedRangeEffects(placedAt);
            maybeStrikeLightning(placedAt.toCenterLocation());
            lastKnownLocation = placedAt;
        }

        if (itemEnt != null && holder == null && !placedOk) {
            renderBeam(itemEnt.getLocation());
            renderRing(itemEnt.getLocation());
            lastKnownLocation = itemEnt.getLocation();
        }

        if (updateTrackers) {
            updateTrackerItems(resolveTrackerTarget(holder, placedOk, itemEnt));
            lastTrackerUpdateTicks = ringPhaseTicks;
        }
    }

    // =======================================================================
    //  Rendering helpers
    // =======================================================================

    /** Beam vertical d'END_ROD partant de la base jusqu'au plafond du monde. */
    private void renderBeam(Location base) {
        World world = base.getWorld();
        if (world == null) return;
        double startY = base.getY();
        double endY = world.getMaxHeight();
        // Throttle: seulement une couche tous les 2 blocs et pas chaque tick à 4Hz, sinon trop dense.
        // On rend partiellement en alternant via ringPhaseTicks pour donner un effet "remontée".
        double offset = (ringPhaseTicks % 8L) / 8.0; // 0..1
        for (double y = startY + offset; y < endY; y += 2.0) {
            Location at = new Location(world, base.getX(), y, base.getZ());
            world.spawnParticle(Particle.END_ROD, at, 1, 0.05, 0.0, 0.05, 0.0);
            // Strate intérieure plus fine
            if ((((long) y) & 1L) == 0L) {
                world.spawnParticle(Particle.DRAGON_BREATH, at, 1, 0.0, 0.0, 0.0, 0.0, 1.0f);
            }
        }
    }

    /** Anneau de particules tournant autour de la base (placed/dropped). */
    private void renderRing(Location base) {
        World world = base.getWorld();
        if (world == null) return;
        int points = 12;
        double radius = 1.6;
        double angleStep = (Math.PI * 2.0) / points;
        double phase = (ringPhaseTicks % 80L) * (Math.PI * 2.0 / 80.0);
        double yOffset = Math.sin(ringPhaseTicks * 0.05) * 0.25;
        for (int i = 0; i < points; i++) {
            double a = phase + i * angleStep;
            double x = base.getX() + Math.cos(a) * radius;
            double z = base.getZ() + Math.sin(a) * radius;
            Location at = new Location(world, x, base.getY() + 0.5 + yOffset, z);
            world.spawnParticle(Particle.SCULK_SOUL, at, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // Anneau extérieur, contre-rotation
        double radius2 = 2.4;
        double phase2 = -phase;
        for (int i = 0; i < points; i++) {
            double a = phase2 + i * angleStep;
            double x = base.getX() + Math.cos(a) * radius2;
            double z = base.getZ() + Math.sin(a) * radius2;
            Location at = new Location(world, x, base.getY() + 0.2, z);
            world.spawnParticle(Particle.PORTAL, at, 1, 0.0, 0.0, 0.0, 0.05);
        }
        // Pulse central
        if ((ringPhaseTicks % 20L) == 0L) {
            world.spawnParticle(Particle.FLASH, base, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
            world.playSound(base, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 0.8f);
        }
        // Bzzzz grave (conduit ambient) toutes les 3s — couche basse fréquence pour l'aspect "ça vibre"
        if ((ringPhaseTicks % 60L) == 0L) {
            world.playSound(base, Sound.BLOCK_CONDUIT_AMBIENT, 1.6f, 0.5f);
        }
        // Pulse profond + tonalité métallique tous les 10s
        if ((ringPhaseTicks % 200L) == 0L) {
            world.playSound(base, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 2.0f, 0.55f);
            world.playSound(base, Sound.ITEM_TRIDENT_THUNDER, 0.4f, 1.6f);
        }
    }

    private void renderAltarAura(Location eggLoc) {
        World world = eggLoc.getWorld();
        if (world == null) return;
        Location center = eggLoc.toCenterLocation();
        double phase = (ringPhaseTicks % 120L) * (Math.PI * 2.0 / 120.0);

        for (int[] c : CORNERS) {
            double bx = eggLoc.getBlockX() + c[0] + 0.5;
            double bz = eggLoc.getBlockZ() + c[1] + 0.5;
            Location beaconTop = new Location(world, bx, eggLoc.getBlockY() + 1.25, bz);
            world.spawnParticle(Particle.END_ROD, beaconTop, 2, 0.08, 0.25, 0.08, 0.02);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, beaconTop, 1, 0.12, 0.12, 0.12, 0.01);

            double sidePhase = phase + Math.atan2(c[1], c[0]);
            Location arc = new Location(
                    world,
                    center.getX() + Math.cos(sidePhase) * 2.8,
                    center.getY() + 0.7 + Math.sin(ringPhaseTicks * 0.08) * 0.25,
                    center.getZ() + Math.sin(sidePhase) * 2.8
            );
            world.spawnParticle(Particle.DRAGON_BREATH, arc, 1, 0.0, 0.0, 0.0, 0.0, 1.0f);
        }

        if ((ringPhaseTicks % 40L) == 0L) {
            world.spawnParticle(Particle.FLASH, center, 1, 0.25, 0.25, 0.25, 0, Color.WHITE);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0.0, 1.1, 0.0), 45, 0.45, 0.9, 0.45, 0.08);
            world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.3f, 0.65f);
            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.55f);
        }

        if ((ringPhaseTicks % 100L) == 0L) {
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.55f, 0.45f);
            world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.35f);
        }
    }

    private void renderHolderParticles(Player holder) {
        Location base = holder.getLocation();
        Location at = base.clone().add(0.0, 1.0, 0.0);
        World world = holder.getWorld();
        double phase = (ringPhaseTicks % 60L) * (Math.PI * 2.0 / 60.0);
        int points = 12;
        for (int ring = 0; ring < 3; ring++) {
            double radius = 0.75 + ring * 0.35;
            double y = at.getY() + ring * 0.45;
            for (int i = 0; i < points; i++) {
                double a = phase * (ring % 2 == 0 ? 1.0 : -1.0) + i * (Math.PI * 2.0 / points);
                double x = at.getX() + Math.cos(a) * radius;
                double z = at.getZ() + Math.sin(a) * radius;
                Location particle = new Location(world, x, y, z);
                world.spawnParticle(Particle.DRAGON_BREATH, particle, 1, 0.0, 0.0, 0.0, 0.0, 1.0f);
                if ((i & 3) == 0) {
                    world.spawnParticle(Particle.END_ROD, particle, 1, 0.0, 0.0, 0.0, 0.02);
                }
            }
        }
        for (double y = base.getY() + 0.2; y < base.getY() + 10.0; y += 1.25) {
            world.spawnParticle(Particle.END_ROD, new Location(world, base.getX(), y, base.getZ()), 1, 0.05, 0.0, 0.05, 0.01);
        }
        if ((ringPhaseTicks % 40L) == 0L) {
            world.spawnParticle(Particle.FLASH, at, 1, 0.2, 0.4, 0.2, 0.0, Color.WHITE);
        }
        if ((ringPhaseTicks % 80L) == 0L) {
            world.playSound(at, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 1.4f);
        }
    }

    // =======================================================================
    //  Effect application
    // =======================================================================

    private void applyHolderEffects(Player holder) {
        // 2.5 ❤ d'absorption pegged (jamais en dessous de 5).
        if (holder.getAbsorptionAmount() < 5.0) {
            holder.setAbsorptionAmount(5.0);
        }
        holder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false, true));
        applyBeaconEffects(holder);
    }

    private void applyTeamEffects(Player holder) {
        TeamManager tm = plugin.teams();
        PlayerData pd = plugin.players() != null ? plugin.players().get(holder) : null;
        if (pd == null || tm == null) return;
        String teamId = pd.teamId();
        if (teamId == null || teamId.isBlank()) return;

        for (TeamManager.Member member : tm.members(teamId)) {
            if (member.uuid().equals(holder.getUniqueId())) continue;
            Player mate = Bukkit.getPlayer(member.uuid());
            if (mate == null || !mate.isOnline()) continue;
            applyBeaconEffects(mate);
        }
    }

    private void applyPlacedRangeEffects(Location at) {
        // Quand l'œuf est posé : tout le monde dans le rayon (incluant ennemis) reçoit les buffs ?
        // Non — le user veut que ça reste réservé au porteur + sa team. On limite donc aux membres
        // de la team du dernier porteur, et au lastHolder lui-même s'il est en ligne et dans le rayon.
        if (lastHolder == null) return;
        Player owner = Bukkit.getPlayer(lastHolder);
        Set<UUID> targets = new HashSet<>();
        if (owner != null && owner.isOnline()) targets.add(owner.getUniqueId());

        TeamManager tm = plugin.teams();
        PlayerData pd = (owner != null && plugin.players() != null) ? plugin.players().get(owner) : null;
        if (pd == null && plugin.players() != null) pd = plugin.players().loadOffline(lastHolder);
        if (pd != null && tm != null && pd.teamId() != null) {
            for (TeamManager.Member member : tm.members(pd.teamId())) {
                targets.add(member.uuid());
            }
        }

        World world = at.getWorld();
        if (world == null) return;
        double rangeSq = BEACON_RANGE * BEACON_RANGE;
        for (UUID uuid : targets) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getWorld() != world) continue;
            if (p.getLocation().distanceSquared(at) > rangeSq) continue;
            applyBeaconEffects(p);
        }
    }

    private void applyBeaconEffects(Player target) {
        for (PotionEffectType type : BEACON_EFFECTS) {
            target.addPotionEffect(new PotionEffect(type, 60, 0, true, false, true));
        }
    }

    // =======================================================================
    //  Respawn pipeline
    // =======================================================================

    private void announceCountdown(long secondsLeft) {
        if (secondsLeft == lastBroadcastSecondsLeft) return;
        boolean tick = secondsLeft == 60L
                || secondsLeft == 30L
                || secondsLeft == 15L
                || secondsLeft == 10L
                || (secondsLeft <= 5L && secondsLeft > 0L);
        if (!tick) return;
        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<gray>Respawn de l'<gradient:#a78bfa:#67e8f9>œuf</gradient> dans <yellow>" + secondsLeft + "s</yellow>"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, secondsLeft <= 5L ? 1.4f : 1.0f);
        }
        lastBroadcastSecondsLeft = secondsLeft;
    }

    private void executeRespawn(boolean force) {
        if (!force && adoptVisibleTrackedEgg("respawn")) {
            return;
        }
        if (respawnLocation == null) {
            ensureDefaultRespawnLocation();
        }
        if (respawnLocation == null || respawnLocation.getWorld() == null) {
            plugin.getLogger().warning("DragonEgg: respawn deferred — autel not ready, will retry from LOST tick.");
            state = State.LOST;
            respawnAt = 0L;
            saveState();
            return;
        }
        purgeVisibleEggItems();
        removePlacedEgg(true);
        tearDownAltar(true);
        Location at = respawnLocation.clone();
        Block block = at.getBlock();
        placedAt = block.getLocation();
        state = State.PRESENT;
        respawnAt = 0L;
        currentHolder = null;
        itemEntityUuid = null;
        lastHolder = null;
        lastKnownLocation = placedAt;
        lastBroadcastSecondsLeft = -1L;
        placedReclaimAt = 0L;

        buildAltar(placedAt);
        block.setType(Material.DRAGON_EGG, false);
        playRespawnEffect(placedAt);
        playPlacedLightningBurst(placedAt.toCenterLocation(), 16, 10.0);
        scheduleNextLightning();

        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<gradient:#a78bfa:#67e8f9><bold>L'Œuf du Dragon est de retour !</bold></gradient>"));
        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<gray>Le signal est de nouveau actif. Utilise un <gradient:#a78bfa:#67e8f9>Traqueur de l'Œuf</gradient> pour le chercher.</gray>"));
        saveState();
    }

    // =======================================================================
    //  Inventory helpers
    // =======================================================================

    private ItemStack findEggInInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (ItemStack stack : contents) {
            if (item.isTracked(stack)) return stack;
        }
        return null;
    }

    private ItemStack takeFromInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!item.isTracked(stack)) continue;
            ItemStack removed = stack.clone();
            inv.setItem(i, null);
            return removed;
        }
        return null;
    }

    public boolean inventoryContainsEgg(Player player) {
        return findEggInInventory(player) != null;
    }

    private void markHeldBy(Player player) {
        if (player == null) return;
        currentHolder = player.getUniqueId();
        lastHolder = currentHolder;
        lastKnownLocation = player.getLocation();
        itemEntityUuid = null;
        placedAt = null;
        placedReclaimAt = 0L;
        if (state != State.PRESENT) state = State.PRESENT;
    }

    private void markDropped(Item entity) {
        if (entity == null) return;
        currentHolder = null;
        itemEntityUuid = entity.getUniqueId();
        lastKnownLocation = entity.getLocation();
        placedAt = null;
        placedReclaimAt = 0L;
        if (state != State.PRESENT) state = State.PRESENT;
    }

    private Player findOnlineEggHolder() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (findEggInInventory(player) != null) {
                return player;
            }
        }
        return null;
    }

    private int purgeVisibleEggItems() {
        int removed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean removedFromPlayer = false;
            for (int i = 0; i < contents.length; i++) {
                if (item.isTracked(contents[i])) {
                    player.getInventory().setItem(i, null);
                    removed++;
                    removedFromPlayer = true;
                }
            }
            if (removedFromPlayer || forcedGlowingHolders.contains(player.getUniqueId())) {
                clearForcedGlow(player.getUniqueId());
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Item drop : world.getEntitiesByClass(Item.class)) {
                if (!drop.isDead() && item.isTracked(drop.getItemStack())) {
                    drop.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private int removePlacedEgg(boolean loadChunk) {
        if (placedAt == null || placedAt.getWorld() == null) return 0;
        World world = placedAt.getWorld();
        int chunkX = placedAt.getBlockX() >> 4;
        int chunkZ = placedAt.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            if (!loadChunk) return 0;
            world.loadChunk(chunkX, chunkZ);
        }
        if (placedAt.getBlock().getType() != Material.DRAGON_EGG) return 0;
        placedAt.getBlock().setType(Material.AIR, false);
        return 1;
    }

    private void clearForcedGlow(UUID uuid) {
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.setGlowing(false);
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
        forcedGlowingHolders.remove(uuid);
    }

    private void clearStaleForcedGlow(UUID activeHolder) {
        if (forcedGlowingHolders.isEmpty()) return;
        for (UUID uuid : new ArrayList<>(forcedGlowingHolders)) {
            if (activeHolder != null && activeHolder.equals(uuid)) continue;
            clearForcedGlow(uuid);
        }
    }

    // =======================================================================
    //  Misc helpers
    // =======================================================================

    private Item findItemEntity(UUID uuid) {
        for (World w : Bukkit.getWorlds()) {
            org.bukkit.entity.Entity e = w.getEntity(uuid);
            if (e instanceof Item it && !e.isDead()) return it;
        }
        return null;
    }

    private boolean adoptVisibleTrackedEgg(String reason) {
        Player holder = findOnlineEggHolder();
        if (holder != null) {
            tearDownAltar(true);
            markHeldBy(holder);
            respawnAt = 0L;
            lastBroadcastSecondsLeft = -1L;
            plugin.getLogger().info("DragonEgg: skipped " + reason + " respawn; tracked egg is held by " + holder.getName());
            saveState();
            return true;
        }

        Item drop = findTrackedItemEntity();
        if (drop != null) {
            tearDownAltar(true);
            markDropped(drop);
            respawnAt = 0L;
            lastBroadcastSecondsLeft = -1L;
            plugin.getLogger().info("DragonEgg: skipped " + reason + " respawn; tracked egg already exists as an item");
            saveState();
            return true;
        }

        if (placedAt != null
                && placedAt.getWorld() != null
                && placedAt.getWorld().isChunkLoaded(placedAt.getBlockX() >> 4, placedAt.getBlockZ() >> 4)
                && placedAt.getBlock().getType() == Material.DRAGON_EGG) {
            state = State.PRESENT;
            currentHolder = null;
            itemEntityUuid = null;
            respawnAt = 0L;
            lastKnownLocation = placedAt;
            lastBroadcastSecondsLeft = -1L;
            scheduleNextLightning();
            plugin.getLogger().info("DragonEgg: skipped " + reason + " respawn; tracked egg is already placed");
            saveState();
            return true;
        }

        return false;
    }

    private Item findTrackedItemEntity() {
        for (World world : Bukkit.getWorlds()) {
            for (Item drop : world.getEntitiesByClass(Item.class)) {
                if (!drop.isDead() && item.isTracked(drop.getItemStack())) {
                    return drop;
                }
            }
        }
        return null;
    }

    private Location resolveTrackerTarget(Player holder, boolean placedOk, Item itemEnt) {
        if (holder != null) return holder.getLocation();
        if (placedOk && placedAt != null) return placedAt.toCenterLocation();
        if (itemEnt != null) return itemEnt.getLocation();
        return lastKnownLocation == null ? null : lastKnownLocation.clone();
    }

    private void updateTrackerItems(Location target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hasTracker = false;
            PlayerInventory inv = player.getInventory();
            ItemStack[] storage = inv.getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                ItemStack stack = storage[i];
                if (!item.isTracker(stack)) continue;
                hasTracker = true;
                item.pointTracker(stack, target);
                inv.setItem(i, stack);
            }

            ItemStack offhand = inv.getItemInOffHand();
            if (item.isTracker(offhand)) {
                hasTracker = true;
                item.pointTracker(offhand, target);
                inv.setItemInOffHand(offhand);
            }

            if (target != null && target.getWorld() != null && player.getWorld().equals(target.getWorld())) {
                player.setCompassTarget(target);
            }
            if (hasTracker && isHoldingTracker(player)) {
                sendTrackerActionBar(player, target);
            }
        }
    }

    private boolean isHoldingTracker(Player player) {
        PlayerInventory inv = player.getInventory();
        return item.isTracker(inv.getItemInMainHand()) || item.isTracker(inv.getItemInOffHand());
    }

    private void sendTrackerActionBar(Player player, Location target) {
        if (target == null || target.getWorld() == null || state == State.DESTROYED || state == State.LOST) {
            player.sendActionBar(Msg.mm("<dark_gray>Traqueur :</dark_gray> <gray>aucun signal stable.</gray>"));
            return;
        }

        String subject = trackerSubject();
        if (!player.getWorld().equals(target.getWorld())) {
            player.sendActionBar(Msg.mm("<dark_gray>Traqueur :</dark_gray> <yellow>" + subject + "</yellow> <gray>dans <white>" + target.getWorld().getName() + "</white>.</gray>"));
            return;
        }

        int distance = (int) Math.round(player.getLocation().distance(target));
        player.sendActionBar(Msg.mm("<dark_gray>Traqueur :</dark_gray> <yellow>" + subject + "</yellow> <gray>à <white>" + distance + "m</white>.</gray>"));
    }

    private String trackerSubject() {
        if (currentHolder != null) {
            Player holder = Bukkit.getPlayer(currentHolder);
            return holder != null ? holder.getName() : "porteur inconnu";
        }
        if (placedAt != null) return "œuf posé";
        if (itemEntityUuid != null) return "œuf au sol";
        return "dernier signal";
    }

    private void ensureDefaultRespawnLocation() {
        if (respawnLocation != null && respawnLocation.getWorld() != null) return;
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("dragonegg.respawn.world", "world_the_end");
        World world = plugin.resolveWorld(worldName, World.Environment.THE_END);
        if (world == null) world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) return;
        double x = cfg.getDouble("dragonegg.respawn.x", 0.5);
        double y = cfg.getDouble("dragonegg.respawn.y", 67.0);
        double z = cfg.getDouble("dragonegg.respawn.z", 0.5);
        respawnLocation = new Location(world, x, y, z);
    }

    private Location findGroundFor(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 2, origin.getBlockY() + 1);
        for (int y = startY; y > world.getMinHeight() + 1; y--) {
            Block at = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            if (at.getType().isAir() && below.getType().isSolid()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    // =======================================================================
    //  Altar structure (4 beacons + iron base + stained glass canopy)
    // =======================================================================

    private void buildAltar(Location eggLoc) {
        if (eggLoc == null || eggLoc.getWorld() == null) return;
        // Si un autel est déjà en mémoire (depuis avant), on le démonte d'abord pour ne pas
        // perdre la trace des anciens blocs.
        if (!altarBackup.isEmpty()) tearDownAltar(true);

        World world = eggLoc.getWorld();
        int cx = eggLoc.getBlockX();
        int cy = eggLoc.getBlockY();
        int cz = eggLoc.getBlockZ();

        // Vérifie que la chunk est chargée — sinon on attendra le prochain tick.
        if (!world.isChunkLoaded(cx >> 4, cz >> 4)) return;

        // Layer y-2 : 5x5 polished blackstone bricks (base décorative).
        for (int dx = -ALTAR_RADIUS; dx <= ALTAR_RADIUS; dx++) {
            for (int dz = -ALTAR_RADIUS; dz <= ALTAR_RADIUS; dz++) {
                replaceForAltar(world, cx + dx, cy - 2, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // Layer y-1 : 5x5 fer (base puissance pour les 4 balises tier 1).
        for (int dx = -ALTAR_RADIUS; dx <= ALTAR_RADIUS; dx++) {
            for (int dz = -ALTAR_RADIUS; dz <= ALTAR_RADIUS; dz++) {
                replaceForAltar(world, cx + dx, cy - 1, cz + dz, Material.IRON_BLOCK);
            }
        }
        // Layer y : 4 balises aux coins diagonaux (l'œuf reste au centre).
        for (int[] c : CORNERS) {
            replaceForAltar(world, cx + c[0], cy, cz + c[1], Material.BEACON);
            // Coté de l'œuf (croix) : on garantit l'air pour pas que la pose détruise un bloc voisin.
        }
        int[][] cardinals = { {-1, 0}, {+1, 0}, {0, -1}, {0, +1} };
        for (int[] c : cardinals) {
            Block side = world.getBlockAt(cx + c[0], cy, cz + c[1]);
            if (!side.getType().isAir()) {
                replaceForAltar(world, cx + c[0], cy, cz + c[1], Material.AIR);
            }
        }
        // Layer y+1 : verre teinté magenta pour colorer les beams.
        for (int[] c : CORNERS) {
            replaceForAltar(world, cx + c[0], cy + 1, cz + c[1], Material.MAGENTA_STAINED_GLASS);
        }
        clearBeaconBeams(eggLoc, true);

        world.playSound(eggLoc.toCenterLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.6f);
        world.playSound(eggLoc.toCenterLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
        world.spawnParticle(Particle.FLASH, eggLoc.toCenterLocation(), 2, 0.5, 0.5, 0.5, 0, Color.WHITE);
    }

    private void maintainBeaconBeams(Location eggLoc) {
        int cleared = clearBeaconBeams(eggLoc, false);
        if (cleared <= 0 || eggLoc.getWorld() == null) return;
        Location center = eggLoc.toCenterLocation();
        eggLoc.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0.0, 2.0, 0.0), Math.min(80, cleared * 6), 1.6, 1.5, 1.6, 0.08);
        eggLoc.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
    }

    private int clearBeaconBeams(Location eggLoc, boolean preserveOriginals) {
        if (eggLoc == null || eggLoc.getWorld() == null) return 0;
        World world = eggLoc.getWorld();
        int cx = eggLoc.getBlockX();
        int cy = eggLoc.getBlockY();
        int cz = eggLoc.getBlockZ();
        int cleared = 0;

        for (int[] c : CORNERS) {
            int x = cx + c[0];
            int z = cz + c[1];
            for (int y = cy + 2; y < world.getMaxHeight(); y++) {
                Block b = world.getBlockAt(x, y, z);
                if (b.getType().isAir()) continue;
                if (preserveOriginals) {
                    replaceForAltar(world, x, y, z, Material.AIR);
                } else {
                    b.setType(Material.AIR, false);
                }
                cleared++;
            }
        }
        return cleared;
    }

    private void tearDownAltar() {
        tearDownAltar(false);
    }

    private void tearDownAltar(boolean loadChunks) {
        if (altarBackup.isEmpty()) return;
        // Restore en ordre inverse (LIFO) pour les soucis de physics (verre avant iron, etc.).
        List<Map.Entry<Location, BlockData>> entries = new ArrayList<>(altarBackup.entrySet());
        Collections.reverse(entries);
        Set<Location> restored = new HashSet<>();
        for (Map.Entry<Location, BlockData> e : entries) {
            Location loc = e.getKey();
            if (loc.getWorld() == null) continue;
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                if (!loadChunks) continue;
                loc.getWorld().loadChunk(chunkX, chunkZ);
            }
            Block b = loc.getBlock();
            try {
                b.setBlockData(e.getValue(), false);
            } catch (Exception ignored) {
                b.setType(Material.AIR, false);
            }
            restored.add(loc);
        }
        for (Location loc : restored) {
            altarBackup.remove(loc);
        }
    }

    private void replaceForAltar(World world, int x, int y, int z, Material mat) {
        Block b = world.getBlockAt(x, y, z);
        Location key = new Location(world, x, y, z);
        if (!altarBackup.containsKey(key)) {
            altarBackup.put(key, b.getBlockData().clone());
        }
        if (b.getType() != mat) {
            b.setType(mat, false);
        }
    }

    private void playRespawnEffect(Location at) {
        World world = at.getWorld();
        if (world == null) return;
        Location center = at.toCenterLocation();
        world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.4f, 1.3f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.1f, 0.8f);
        world.spawnParticle(Particle.DRAGON_BREATH, center, 120, 0.8, 0.8, 0.8, 0.08, 1.0f);
        world.spawnParticle(Particle.END_ROD, center, 80, 0.4, 1.2, 0.4, 0.08);
        world.spawnParticle(Particle.FLASH, center, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
    }

    private void playRemovalOutro(Location at) {
        if (at == null || at.getWorld() == null) return;
        World world = at.getWorld();
        Location center = at.toCenterLocation();

        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 2.8f, 0.45f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.35f);
        world.spawnParticle(Particle.FLASH, center, 3, 0.35, 0.35, 0.35, 0, Color.WHITE);
        world.spawnParticle(Particle.DRAGON_BREATH, center, 260, 1.8, 1.0, 1.8, 0.16, 1.0f);

        for (int[] c : CORNERS) {
            Location corner = new Location(world, at.getBlockX() + c[0] + 0.5, at.getBlockY() + 1.0, at.getBlockZ() + c[1] + 0.5);
            world.strikeLightningEffect(corner);
            world.spawnParticle(Particle.END_ROD, corner, 70, 0.25, 1.8, 0.25, 0.12);
            world.spawnParticle(Particle.SCULK_SOUL, corner, 35, 0.3, 0.6, 0.3, 0.04);
        }

        for (int step = 0; step < 18; step++) {
            final int tick = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double radius = Math.max(0.35, 4.8 - tick * 0.25);
                double y = center.getY() + 0.25 + tick * 0.12;
                int points = 36;
                for (int i = 0; i < points; i++) {
                    double angle = i * (Math.PI * 2.0 / points) - tick * 0.18;
                    Location p = new Location(
                            world,
                            center.getX() + Math.cos(angle) * radius,
                            y,
                            center.getZ() + Math.sin(angle) * radius
                    );
                    world.spawnParticle(Particle.PORTAL, p, 1, 0.0, 0.0, 0.0, 0.08);
                    if ((i & 3) == 0) {
                        world.spawnParticle(Particle.END_ROD, p, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }, tick);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 2.4f, 0.55f);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.35, 0.35, 0.35, 0);
            world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0.0, 0.35, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
            playReverseShockwave(center);
        }, 22L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int y = 0; y < 48; y += 2) {
                Location p = center.clone().add(0.0, y, 0.0);
                world.spawnParticle(Particle.DRAGON_BREATH, p, 2, 0.15, 0.0, 0.15, 0.01, 1.0f);
                world.spawnParticle(Particle.END_ROD, p, 1, 0.08, 0.0, 0.08, 0.0);
            }
            world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.4f, 0.6f);
        }, 38L);
    }

    // =======================================================================
    //  Placement intro
    // =======================================================================

    private void playPlacementIntro(Location at) {
        World world = at.getWorld();
        if (world == null) return;
        final Location center = at.toCenterLocation();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 5.5f, 0.38f);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.8f, 0.5f);
            world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.4f, 1.15f);
            world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.7f, 0.75f);
            world.spawnParticle(Particle.DRAGON_BREATH, center, 650, 3.2, 1.8, 3.2, 0.22, 1.0f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 4, 0.55, 0.55, 0.55, 0);
            world.spawnParticle(Particle.FLASH, center, 6, 0.7, 0.7, 0.7, 0, Color.WHITE);
            world.strikeLightningEffect(center);
            playIntroVortex(center);
            playIntroRuneCircle(center);
        }, 0L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int[] c : CORNERS) {
                Location corner = new Location(world, at.getX() + c[0] + 0.5, at.getY() + 0.5, at.getZ() + c[1] + 0.5);
                world.spawnParticle(Particle.FLASH, corner, 3, 0, 0, 0, 0, Color.WHITE);
                world.spawnParticle(Particle.END_ROD, corner, 90, 0.35, 1.2, 0.35, 0.18);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, corner, 45, 0.22, 0.35, 0.22, 0.06);
                world.spawnParticle(Particle.SCULK_SOUL, corner, 30, 0.2, 0.35, 0.2, 0.04);
            }
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 3.3f, 0.52f);
            world.playSound(center, Sound.BLOCK_CONDUIT_ACTIVATE, 2.5f, 0.6f);
            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0f, 0.48f);
            playIntroBeaconAscension(at);
        }, 15L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playIntroLightningCage(at, 6.0, 8);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.2f, 0.62f);
            world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 3.0f, 0.42f);
        }, 25L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 2.6f, 1.15f);
            world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 0.8f);
            world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0.0, 0.35, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
            playShockwave(center);
            playIntroRuneCircle(center);
        }, 40L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playIntroSkyRift(center, 96);
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.6f, 0.28f);
            world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 3.0f, 0.46f);
        }, 58L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playIntroLightningCage(at, 9.0, 12);
            playShockwave(center);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 3, 0.6, 0.6, 0.6, 0);
            world.spawnParticle(Particle.DRAGON_BREATH, center, 420, 2.2, 2.0, 2.2, 0.18, 1.0f);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.6f, 0.55f);
            world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 3.4f, 0.48f);
        }, 82L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playIntroSkyRift(center, 128);
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.7f, 1.22f);
            world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 3.6f, 0.52f);
            world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 3.5f, 0.52f);
            world.spawnParticle(Particle.FLASH, center, 8, 1.0, 1.0, 1.0, 0, Color.WHITE);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0.0, 1.0, 0.0), 220, 1.3, 2.6, 1.3, 0.18);
        }, 108L);
    }

    private void playIntroVortex(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        for (int t = 0; t < 44; t++) {
            final int tick = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double baseRadius = 5.8 - Math.min(3.2, tick * 0.07);
                double height = 0.35 + tick * 0.09;
                int points = 30;
                for (int i = 0; i < points; i++) {
                    double angle = i * (Math.PI * 2.0 / points) + tick * 0.28;
                    double radius = baseRadius + Math.sin(tick * 0.18 + i) * 0.35;
                    Location p = new Location(
                            world,
                            center.getX() + Math.cos(angle) * radius,
                            center.getY() + height,
                            center.getZ() + Math.sin(angle) * radius
                    );
                    world.spawnParticle(Particle.PORTAL, p, 1, 0.0, 0.0, 0.0, 0.12);
                    if ((i % 6) == 0) {
                        world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0.0, 0.0, 0.0, 0.0, 1.0f);
                    }
                }
                if ((tick % 7) == 0) {
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.45f + tick * 0.01f);
                }
            }, tick);
        }
    }

    private void playIntroRuneCircle(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        for (int ring = 0; ring < 4; ring++) {
            final int step = ring;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double radius = 2.5 + step * 1.6;
                int points = 48 + step * 12;
                for (int i = 0; i < points; i++) {
                    double angle = i * (Math.PI * 2.0 / points);
                    Location p = new Location(
                            world,
                            center.getX() + Math.cos(angle) * radius,
                            center.getY() + 0.12,
                            center.getZ() + Math.sin(angle) * radius
                    );
                    world.spawnParticle(Particle.SCULK_SOUL, p, 1, 0.0, 0.0, 0.0, 0.0);
                    if ((i % 8) == 0) {
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, p.clone().add(0.0, 0.15, 0.0), 2, 0.05, 0.05, 0.05, 0.02);
                    }
                }
            }, step * 4L);
        }
    }

    private void playIntroBeaconAscension(Location at) {
        World world = at.getWorld();
        if (world == null) return;
        for (int t = 0; t < 34; t++) {
            final int tick = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double y = at.getBlockY() + 1.0 + tick * 1.55;
                for (int[] c : CORNERS) {
                    Location p = new Location(world, at.getBlockX() + c[0] + 0.5, y, at.getBlockZ() + c[1] + 0.5);
                    world.spawnParticle(Particle.END_ROD, p, 4, 0.08, 0.18, 0.08, 0.02);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 2, 0.12, 0.12, 0.12, 0.01);
                }
            }, tick);
        }
    }

    private void playIntroLightningCage(Location at, double radius, int strikes) {
        World world = at.getWorld();
        if (world == null) return;
        Location center = at.toCenterLocation();
        for (int i = 0; i < strikes; i++) {
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = idx * (Math.PI * 2.0 / strikes);
                Location target = new Location(
                        world,
                        center.getX() + Math.cos(angle) * radius,
                        center.getY(),
                        center.getZ() + Math.sin(angle) * radius
                );
                world.strikeLightningEffect(target);
                world.spawnParticle(Particle.FLASH, target, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
                world.spawnParticle(Particle.END_ROD, target.clone().add(0.0, 1.2, 0.0), 45, 0.25, 1.4, 0.25, 0.12);
            }, idx * 2L);
        }
    }

    private void playIntroSkyRift(Location center, int height) {
        World world = center.getWorld();
        if (world == null) return;
        int maxHeight = Math.min(height, world.getMaxHeight() - center.getBlockY() - 2);
        for (int y = 0; y < maxHeight; y += 2) {
            double twist = y * 0.16;
            Location core = center.clone().add(Math.cos(twist) * 0.18, y, Math.sin(twist) * 0.18);
            world.spawnParticle(Particle.END_ROD, core, 3, 0.12, 0.0, 0.12, 0.01);
            if ((y % 6) == 0) {
                world.spawnParticle(Particle.DRAGON_BREATH, core, 2, 0.35, 0.0, 0.35, 0.01, 1.0f);
            }
        }
    }

    private void playShockwave(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        for (int t = 0; t < 18; t++) {
            final int tick = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double radius = 1.0 + tick * 1.2;
                int points = Math.min(96, 24 + tick * 4);
                for (int i = 0; i < points; i++) {
                    double a = i * (Math.PI * 2.0 / points);
                    double x = center.getX() + Math.cos(a) * radius;
                    double z = center.getZ() + Math.sin(a) * radius;
                    Location p = new Location(world, x, center.getY() + 0.2, z);
                    world.spawnParticle(Particle.SONIC_BOOM, p, 1, 0, 0, 0, 0);
                    if ((i & 3) == 0) {
                        world.spawnParticle(Particle.CLOUD, p, 1, 0, 0, 0, 0);
                    }
                }
            }, tick);
        }
    }

    private void playReverseShockwave(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        for (int t = 0; t < 16; t++) {
            final int tick = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double radius = Math.max(0.4, 12.0 - tick * 0.7);
                int points = 80;
                for (int i = 0; i < points; i++) {
                    double a = i * (Math.PI * 2.0 / points);
                    double x = center.getX() + Math.cos(a) * radius;
                    double z = center.getZ() + Math.sin(a) * radius;
                    Location p = new Location(world, x, center.getY() + 0.25, z);
                    world.spawnParticle(Particle.SCULK_SOUL, p, 1, 0, 0, 0, 0.0);
                    if ((i & 7) == 0) {
                        world.spawnParticle(Particle.CLOUD, p, 1, 0, 0, 0, 0.0);
                    }
                }
            }, tick);
        }
    }

    // =======================================================================
    //  Lightning (cosmétique)
    // =======================================================================

    private void scheduleNextLightning() {
        long range = LIGHTNING_MAX_TICKS - LIGHTNING_MIN_TICKS;
        nextLightningTicks = ringPhaseTicks + LIGHTNING_MIN_TICKS + (long) (random.nextDouble() * range);
    }

    private void playPlacedLightningBurst(Location at, int strikes, double radius) {
        if (at == null || at.getWorld() == null) return;
        World world = at.getWorld();
        for (int i = 0; i < strikes; i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = index * (Math.PI * 2.0 / strikes) + random.nextDouble() * 0.35;
                double spread = radius * (0.45 + random.nextDouble() * 0.55);
                Location target = at.clone().add(Math.cos(angle) * spread, 0.0, Math.sin(angle) * spread);
                world.strikeLightningEffect(target);
                world.spawnParticle(Particle.FLASH, target, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
                world.spawnParticle(Particle.END_ROD, target.clone().add(0.0, 1.0, 0.0), 55, 0.25, 1.5, 0.25, 0.12);
            }, index * 3L);
        }
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.5f, 0.7f);
        world.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 2.4f, 0.55f);
    }

    private void maybeStrikeLightning(Location at) {
        if (nextLightningTicks < 0L) {
            scheduleNextLightning();
            return;
        }
        if (ringPhaseTicks < nextLightningTicks) return;
        World world = at.getWorld();
        if (world == null) return;
        int strikes = 4 + random.nextInt(5);
        for (int i = 0; i < strikes; i++) {
            double dx = (random.nextDouble() - 0.5) * 14.0;
            double dz = (random.nextDouble() - 0.5) * 14.0;
            Location target = at.clone().add(dx, 0, dz);
            world.strikeLightningEffect(target);
            world.spawnParticle(Particle.FLASH, target, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
        }
        for (int[] c : CORNERS) {
            world.strikeLightningEffect(new Location(world, at.getBlockX() + c[0] + 0.5, at.getY(), at.getBlockZ() + c[1] + 0.5));
        }
        world.spawnParticle(Particle.DRAGON_BREATH, at, 160, 1.3, 1.0, 1.3, 0.1, 1.0f);
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 0.75f);
        world.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 0.65f);
        scheduleNextLightning();
    }

    // =======================================================================
    //  Anti-dupe
    // =======================================================================

    /**
     * Conserve UN seul œuf taggé dans l'univers et supprime les copies. Priorité :
     *   placedAt (bloc connu) > inventaire du currentHolder > items dans le monde > inventaires online.
     * On laisse passer si placedAt n'est pas chargé (chunk pas prêt) — re-check au prochain trigger.
     */
    private void deduplicate(String reason) {
        // 1. Collecte tous les œufs taggés visibles.
        record InvSlot(Player player, int slot) {}
        List<InvSlot> inv = new ArrayList<>();
        List<Item> drops = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (item.isTracked(contents[i])) inv.add(new InvSlot(p, i));
            }
        }
        for (World w : Bukkit.getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) {
                if (it.isDead()) continue;
                if (item.isTracked(it.getItemStack())) drops.add(it);
            }
        }

        boolean placedExists = placedAt != null
                && placedAt.getWorld() != null
                && placedAt.getWorld().isChunkLoaded(placedAt.getBlockX() >> 4, placedAt.getBlockZ() >> 4)
                && placedAt.getBlock().getType() == Material.DRAGON_EGG;

        int total = (placedExists ? 1 : 0) + inv.size() + drops.size();
        if (total <= 1) return;

        // 2. Décide qui on garde.
        // Si bloc posé existe → on supprime tous les items (en main + au sol).
        // Sinon, on garde le 1er item d'inventaire trouvé (priorité au currentHolder s'il est là),
        // sinon le 1er drop.
        InvSlot keepInv = null;
        Item keepDrop = null;
        if (!placedExists) {
            if (currentHolder != null) {
                for (InvSlot s : inv) {
                    if (s.player().getUniqueId().equals(currentHolder)) { keepInv = s; break; }
                }
            }
            if (keepInv == null && !inv.isEmpty()) keepInv = inv.get(0);
            if (keepInv == null && !drops.isEmpty()) keepDrop = drops.get(0);
        }

        int removed = 0;
        for (InvSlot s : inv) {
            if (s == keepInv) continue;
            s.player().getInventory().setItem(s.slot(), null);
            removed++;
        }
        for (Item it : drops) {
            if (it == keepDrop) continue;
            it.remove();
            removed++;
        }

        if (removed > 0) {
            plugin.getLogger().info("DragonEgg: dedupe (" + reason + ") removed " + removed + " duplicate egg(s)");
            Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<dark_red>✦</dark_red> <gray>Œuf parasite x<yellow>" + removed + "</yellow> dissout — l'unique <gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient> demeure.</gray>"));
        }
    }

    // =======================================================================
    //  Persistence
    // =======================================================================

    private void loadState() {
        if (!stateFile.exists()) return;
        FileConfiguration yml = YamlConfiguration.loadConfiguration(stateFile);

        String stateRaw = yml.getString("state", "LOST");
        try {
            state = State.valueOf(stateRaw);
        } catch (IllegalArgumentException ignored) {
            state = State.LOST;
        }
        respawnAt = yml.getLong("respawn-at", 0L);
        placedReclaimAt = yml.getLong("placed-reclaim-at", 0L);
        String holderRaw = yml.getString("last-holder", "");
        try {
            lastHolder = (holderRaw == null || holderRaw.isBlank()) ? null : UUID.fromString(holderRaw);
        } catch (IllegalArgumentException ignored) {
            lastHolder = null;
        }
        respawnLocation = readLocation(yml, "respawn-location");
        lastKnownLocation = readLocation(yml, "last-known");
        Location placed = readLocation(yml, "placed-at");
        if (placed != null) placedAt = placed;

        // Charge le backup de l'autel
        altarBackup.clear();
        ConfigurationSection altarSec = yml.getConfigurationSection("altar-backup");
        if (altarSec != null) {
            for (String key : altarSec.getKeys(false)) {
                ConfigurationSection entry = altarSec.getConfigurationSection(key);
                if (entry == null) continue;
                String wname = entry.getString("world");
                if (wname == null) continue;
                World w = Bukkit.getWorld(wname);
                if (w == null) continue;
                int x = entry.getInt("x");
                int y = entry.getInt("y");
                int z = entry.getInt("z");
                String dataStr = entry.getString("data");
                if (dataStr == null || dataStr.isBlank()) continue;
                try {
                    BlockData data = Bukkit.createBlockData(dataStr);
                    altarBackup.put(new Location(w, x, y, z), data);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().log(Level.WARNING, "DragonEgg: bad altar-backup data \"" + dataStr + "\"", ex);
                }
            }
        }
    }

    private void saveState() {
        FileConfiguration yml = new YamlConfiguration();
        yml.set("state", state.name());
        yml.set("respawn-at", respawnAt);
        yml.set("placed-reclaim-at", placedReclaimAt);
        yml.set("last-holder", lastHolder == null ? "" : lastHolder.toString());
        writeLocation(yml, "respawn-location", respawnLocation);
        writeLocation(yml, "last-known", lastKnownLocation);
        writeLocation(yml, "placed-at", placedAt);

        // Sauve le backup de l'autel — clé synthétique (idx) car les YAML keys n'aiment pas les ":".
        if (!altarBackup.isEmpty()) {
            int i = 0;
            for (Map.Entry<Location, BlockData> e : altarBackup.entrySet()) {
                Location loc = e.getKey();
                if (loc.getWorld() == null) continue;
                String base = "altar-backup." + i++;
                yml.set(base + ".world", loc.getWorld().getName());
                yml.set(base + ".x", loc.getBlockX());
                yml.set(base + ".y", loc.getBlockY());
                yml.set(base + ".z", loc.getBlockZ());
                yml.set(base + ".data", e.getValue().getAsString());
            }
        } else {
            yml.set("altar-backup", null);
        }
        try {
            yml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "DragonEgg: failed to save state", e);
        }
    }

    private Location readLocation(FileConfiguration yml, String path) {
        if (!yml.isConfigurationSection(path)) return null;
        String wname = yml.getString(path + ".world");
        if (wname == null) return null;
        World w = Bukkit.getWorld(wname);
        if (w == null) return null;
        double x = yml.getDouble(path + ".x");
        double y = yml.getDouble(path + ".y");
        double z = yml.getDouble(path + ".z");
        float yaw = (float) yml.getDouble(path + ".yaw", 0.0);
        float pitch = (float) yml.getDouble(path + ".pitch", 0.0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    private void writeLocation(FileConfiguration yml, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            yml.set(path, null);
            return;
        }
        yml.set(path + ".world", loc.getWorld().getName());
        yml.set(path + ".x", loc.getX());
        yml.set(path + ".y", loc.getY());
        yml.set(path + ".z", loc.getZ());
        yml.set(path + ".yaw", loc.getYaw());
        yml.set(path + ".pitch", loc.getPitch());
    }
}
