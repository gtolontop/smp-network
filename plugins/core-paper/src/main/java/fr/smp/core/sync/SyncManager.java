package fr.smp.core.sync;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Saves/loads cross-server player state via flat YAML files under shared-data.
 * Synced: inventory, ender chest, xp, health, food, potion effects,
 * selected survival state, flight, air, fire ticks, fall distance, advancement progress is
 * left to vanilla (stored server-local).
 */
public class SyncManager {

    private final SMPCore plugin;
    private final File dataDir;
    private final boolean enabled;
    private final boolean clearOnMissing;
    private final boolean autosaveEnabled;
    private final long autosaveIntervalTicks;
    private final long dirtyDelayTicks;
    private final ConcurrentMap<UUID, BukkitTask> pendingDirtySaves = new ConcurrentHashMap<>();
    private BukkitTask autosaveTask;

    public SyncManager(SMPCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("sync.enabled", true);
        this.clearOnMissing = plugin.getConfig().getBoolean("sync.clear-on-missing", false);
        this.autosaveEnabled = plugin.getConfig().getBoolean("sync.autosave.enabled", true);
        int autosaveSeconds = Math.max(5, plugin.getConfig().getInt("sync.autosave.interval-seconds", 30));
        this.autosaveIntervalTicks = autosaveSeconds * 20L;
        int dirtyDelaySeconds = Math.max(1, plugin.getConfig().getInt("sync.autosave.dirty-delay-seconds", 3));
        this.dirtyDelayTicks = dirtyDelaySeconds * 20L;
        String relative = plugin.getConfig().getString("sync.directory", "../shared-data/players");
        File base = new File(plugin.getServer().getWorldContainer(), relative);
        this.dataDir = base;
        if (enabled && !dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("Could not create sync dir: " + dataDir.getAbsolutePath());
        }
        if (enabled) {
            plugin.getLogger().info("Inventory sync enabled, dir: " + dataDir.getAbsolutePath());
        }
    }

    public boolean isEnabled() { return enabled; }
    public File getSyncDataDir() { return dataDir; }

    public void startAutosave() {
        if (!enabled || !autosaveEnabled || autosaveTask != null) return;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAllOnline,
                autosaveIntervalTicks, autosaveIntervalTicks);
        plugin.getLogger().info("Inventory sync autosave enabled "
                + "(interval=" + (autosaveIntervalTicks / 20) + "s, dirty-delay="
                + (dirtyDelayTicks / 20) + "s)");
    }

    public void stopAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        for (BukkitTask task : pendingDirtySaves.values()) {
            task.cancel();
        }
        pendingDirtySaves.clear();
    }

    public void markDirty(Player player) {
        if (!enabled || !autosaveEnabled || player.hasPermission("smp.sync.bypass")) return;
        // Quand un staff a son hotbar swappé (vanish), on n'écrit JAMAIS la
        // hotbar de service dans le sync cross-serveur — sinon le vrai stuff
        // serait écrasé. Le vrai hotbar est restauré au prochain join via le
        // snapshot YAML local de VanishManager.
        if (plugin.vanish() != null && plugin.vanish().shouldSkipSync(player)) return;
        UUID uuid = player.getUniqueId();
        if (pendingDirtySaves.containsKey(uuid)) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingDirtySaves.remove(uuid);
            Player live = Bukkit.getPlayer(uuid);
            if (live != null && live.isOnline()) {
                save(live);
            }
        }, dirtyDelayTicks);
        BukkitTask previous = pendingDirtySaves.putIfAbsent(uuid, task);
        if (previous != null) task.cancel();
    }

    private File fileFor(UUID uuid) {
        return new File(dataDir, uuid.toString() + ".yml");
    }

    private File backupFor(UUID uuid) {
        return new File(dataDir, uuid.toString() + ".yml.bak");
    }

    /**
     * Snapshot the player's full syncable state into a fresh YamlConfiguration.
     * Safe to call on the main thread only. Reused by {@link #save(Player)} and
     * by InventoryHistoryManager for rollback snapshots.
     */
    public YamlConfiguration captureYaml(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", player.getName());
        yaml.set("updated", System.currentTimeMillis());
        yaml.set("source-server", plugin.getServerType());

        PlayerInventory inv = player.getInventory();
        yaml.set("inventory.contents", inv.getContents());
        yaml.set("inventory.armor", inv.getArmorContents());
        yaml.set("inventory.offhand", inv.getItemInOffHand());
        yaml.set("inventory.heldSlot", inv.getHeldItemSlot());

        yaml.set("enderchest", player.getEnderChest().getContents());

        yaml.set("xp.level", player.getLevel());
        yaml.set("xp.exp", player.getExp());
        yaml.set("xp.totalExperience", player.getTotalExperience());

        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        yaml.set("stats.health", Math.min(player.getHealth(), maxHealth));
        yaml.set("stats.food", player.getFoodLevel());
        yaml.set("stats.saturation", player.getSaturation());
        yaml.set("stats.exhaustion", player.getExhaustion());
        yaml.set("stats.remainingAir", player.getRemainingAir());
        yaml.set("stats.fireTicks", player.getFireTicks());
        yaml.set("stats.fallDistance", player.getFallDistance());

        if (!plugin.isLobby()) {
            yaml.set("gamemode", player.getGameMode().name());
        }
        yaml.set("flight.allowed", player.getAllowFlight());
        yaml.set("flight.flying", player.isFlying());
        yaml.set("flight.speed", player.getFlySpeed());
        yaml.set("walkSpeed", player.getWalkSpeed());

        List<java.util.Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffect e : player.getActivePotionEffects()) {
            effects.add(e.serialize());
        }
        yaml.set("effects", effects);
        return yaml;
    }

    public void save(Player player) {
        if (!enabled) return;
        if (player.hasPermission("smp.sync.bypass")) return;
        if (plugin.vanish() != null && plugin.vanish().shouldSkipSync(player)) return;

        YamlConfiguration yaml = captureYaml(player);

        File target = fileFor(player.getUniqueId());
        File backup = backupFor(player.getUniqueId());
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        try {
            // Backup the current file BEFORE overwriting so we never lose data.
            if (target.exists()) {
                Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to refresh sync backup for " + player.getName() + ": " + e.getMessage());
        }
        try {
            yaml.save(tmp);
            try {
                Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomic) {
                // ATOMIC_MOVE not supported on this filesystem — fall back to a
                // regular replace-move so the data is never silently lost.
                plugin.getLogger().fine("Atomic move unavailable for " + player.getName() + ", falling back to regular move.");
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save sync data for " + player.getName() + ": " + e.getMessage());
        } finally {
            if (tmp.exists() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void load(Player player) {
        if (!enabled) return;
        if (player.hasPermission("smp.sync.bypass")) return;

        YamlConfiguration yaml = loadSnapshot(player);
        if (yaml == null) {
            if (clearOnMissing) {
                applyDefaults(player);
            }
            return;
        }
        applyYaml(player, yaml);
    }

    /**
     * Apply a previously captured YAML snapshot to a live player. Used by
     * {@link #load(Player)} and by InventoryHistoryManager to rollback. Must
     * run on the main thread.
     */
    @SuppressWarnings("unchecked")
    public void applyYaml(Player player, YamlConfiguration yaml) {
        PlayerInventory inv = player.getInventory();

        List<?> contents = yaml.getList("inventory.contents");
        List<?> armor = yaml.getList("inventory.armor");
        List<?> ender = yaml.getList("enderchest");
        inv.clear();
        if (contents != null) {
            ItemStack[] arr = contents.stream()
                .map(o -> o instanceof ItemStack i ? i : null)
                .toArray(ItemStack[]::new);
            inv.setContents(arr);
        }
        if (armor != null) {
            ItemStack[] arr = armor.stream()
                .map(o -> o instanceof ItemStack i ? i : null)
                .toArray(ItemStack[]::new);
            inv.setArmorContents(arr);
        }
        ItemStack offhand = yaml.getItemStack("inventory.offhand");
        if (offhand != null) inv.setItemInOffHand(offhand);
        inv.setHeldItemSlot(Math.max(0, Math.min(8, yaml.getInt("inventory.heldSlot", 0))));

        if (ender != null) {
            player.getEnderChest().clear();
            ItemStack[] arr = ender.stream()
                .map(o -> o instanceof ItemStack i ? i : null)
                .toArray(ItemStack[]::new);
            player.getEnderChest().setContents(arr);
        }

        player.setLevel(yaml.getInt("xp.level", 0));
        player.setExp((float) yaml.getDouble("xp.exp", 0.0));
        player.setTotalExperience(yaml.getInt("xp.totalExperience", 0));

        double maxHealth = 20.0;
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) maxHealth = maxHealthAttr.getValue();
        double saved = yaml.getDouble("stats.health", maxHealth);
        if (saved > 0) player.setHealth(Math.min(saved, maxHealth));
        player.setFoodLevel(yaml.getInt("stats.food", 20));
        player.setSaturation((float) yaml.getDouble("stats.saturation", 5.0));
        player.setExhaustion((float) yaml.getDouble("stats.exhaustion", 0.0));
        player.setRemainingAir(yaml.getInt("stats.remainingAir", player.getMaximumAir()));
        player.setFireTicks(yaml.getInt("stats.fireTicks", 0));
        player.setFallDistance((float) yaml.getDouble("stats.fallDistance", 0.0));

        String gm = yaml.getString("gamemode");
        if (shouldApplyGamemode(yaml, gm)) {
            try { player.setGameMode(GameMode.valueOf(gm)); } catch (IllegalArgumentException ignored) {}
        }
        if (yaml.contains("flight.allowed")) player.setAllowFlight(yaml.getBoolean("flight.allowed"));
        if (yaml.contains("flight.flying")) player.setFlying(yaml.getBoolean("flight.flying"));
        if (yaml.contains("flight.speed")) player.setFlySpeed((float) yaml.getDouble("flight.speed"));
        if (yaml.contains("walkSpeed")) player.setWalkSpeed((float) yaml.getDouble("walkSpeed"));

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        List<?> effects = yaml.getList("effects");
        if (effects != null) {
            for (Object raw : effects) {
                if (raw instanceof java.util.Map<?, ?> map) {
                    try {
                        PotionEffect pe = new PotionEffect((java.util.Map<String, Object>) map);
                        player.addPotionEffect(pe);
                    } catch (Exception ignored) { }
                }
            }
        }
        player.updateInventory();
    }

    private YamlConfiguration loadSnapshot(Player player) {
        File primary = fileFor(player.getUniqueId());
        File backup = backupFor(player.getUniqueId());

        YamlConfiguration primaryYaml = tryLoad(primary);
        boolean primaryValid = isValidSnapshot(primaryYaml);

        YamlConfiguration backupYaml = tryLoad(backup);
        boolean backupValid = isValidSnapshot(backupYaml);

        if (primaryValid && backupValid && shouldPreferBackup(primaryYaml, backupYaml)) {
            plugin.getLogger().warning("Recovered sync data for " + player.getName()
                    + " from backup snapshot because primary source-server="
                    + sourceServer(primaryYaml) + " and backup source-server="
                    + sourceServer(backupYaml) + ".");
            return backupYaml;
        }

        if (primaryValid) {
            return primaryYaml;
        }
        if (primary.exists()) {
            plugin.getLogger().warning("Invalid sync snapshot for " + player.getName()
                    + " in " + primary.getName() + ", trying backup.");
        }

        if (backupValid) {
            plugin.getLogger().warning("Recovered sync data for " + player.getName()
                    + " from backup snapshot.");
            return backupYaml;
        }
        if (primary.exists() || backup.exists()) {
            plugin.getLogger().warning("No valid sync snapshot found for " + player.getName()
                    + "; leaving current in-memory state untouched.");
        }
        return null;
    }

    private YamlConfiguration tryLoad(File file) {
        if (!file.exists()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private boolean isValidSnapshot(YamlConfiguration yaml) {
        return yaml != null
                && yaml.contains("inventory.contents")
                && yaml.contains("inventory.armor")
                && yaml.contains("enderchest");
    }

    private boolean shouldPreferBackup(YamlConfiguration primaryYaml, YamlConfiguration backupYaml) {
        String primarySource = sourceServer(primaryYaml);
        String backupSource = sourceServer(backupYaml);
        if (!plugin.isLobby()
                && primarySource.equalsIgnoreCase("lobby")
                && !backupSource.equalsIgnoreCase("lobby")
                && !backupSource.isBlank()) {
            return true;
        }
        String currentSource = plugin.getServerType();
        return !currentSource.isBlank()
                && !primarySource.equalsIgnoreCase(currentSource)
                && backupSource.equalsIgnoreCase(currentSource);
    }

    private String sourceServer(YamlConfiguration yaml) {
        return yaml == null ? "" : yaml.getString("source-server", "");
    }

    private boolean shouldApplyGamemode(YamlConfiguration yaml, String gamemode) {
        if (gamemode == null || gamemode.isBlank()) {
            return false;
        }
        if (plugin.isLobby()) {
            return false;
        }
        String sourceServer = yaml.getString("source-server", "");
        return !sourceServer.equalsIgnoreCase("lobby");
    }

    private void applyDefaults(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        inv.setItemInOffHand(null);
        inv.setHeldItemSlot(0);

        if (plugin.getConfig().getBoolean("sync.defaults.clear-enderchest", true)) {
            player.getEnderChest().clear();
        }

        player.setLevel(plugin.getConfig().getInt("sync.defaults.xp-level", 0));
        player.setExp((float) plugin.getConfig().getDouble("sync.defaults.xp-exp", 0.0));
        player.setTotalExperience(plugin.getConfig().getInt("sync.defaults.xp-total", 0));

        double maxHealth = 20.0;
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) maxHealth = maxHealthAttr.getValue();

        double health = plugin.getConfig().getDouble("sync.defaults.health", maxHealth);
        player.setHealth(Math.max(0.5, Math.min(health, maxHealth)));
        player.setFoodLevel(plugin.getConfig().getInt("sync.defaults.food", 20));
        player.setSaturation((float) plugin.getConfig().getDouble("sync.defaults.saturation", 20.0));
        player.setExhaustion((float) plugin.getConfig().getDouble("sync.defaults.exhaustion", 0.0));
        player.setRemainingAir(plugin.getConfig().getInt("sync.defaults.remaining-air", player.getMaximumAir()));
        player.setFireTicks(plugin.getConfig().getInt("sync.defaults.fire-ticks", 0));
        player.setFallDistance((float) plugin.getConfig().getDouble("sync.defaults.fall-distance", 0.0));

        String gamemode = plugin.getConfig().getString("sync.defaults.gamemode", "");
        if (gamemode != null && !gamemode.isBlank()) {
            try {
                player.setGameMode(GameMode.valueOf(gamemode.toUpperCase()));
            } catch (IllegalArgumentException ignored) { }
        }

        boolean allowFlight = plugin.getConfig().getBoolean("sync.defaults.allow-flight", false);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && plugin.getConfig().getBoolean("sync.defaults.flying", false));

        if (plugin.getConfig().contains("sync.defaults.fly-speed")) {
            player.setFlySpeed((float) plugin.getConfig().getDouble("sync.defaults.fly-speed"));
        }
        if (plugin.getConfig().contains("sync.defaults.walk-speed")) {
            player.setWalkSpeed((float) plugin.getConfig().getDouble("sync.defaults.walk-speed"));
        }

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.updateInventory();
    }

    public void saveAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) save(p);
    }
}
