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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Saves/loads cross-server player state via flat YAML files under shared-data.
 * Synced: inventory, ender chest, xp, health, food, potion effects,
 * gamemode, flight, air, fire ticks, fall distance, advancement progress is
 * left to vanilla (stored server-local).
 */
public class SyncManager {

    private final SMPCore plugin;
    private final File dataDir;
    private final boolean enabled;

    public SyncManager(SMPCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("sync.enabled", true);
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

    private File fileFor(UUID uuid) {
        return new File(dataDir, uuid.toString() + ".yml");
    }

    public void save(Player player) {
        if (!enabled) return;
        if (player.hasPermission("smp.sync.bypass")) return;

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

        yaml.set("gamemode", player.getGameMode().name());
        yaml.set("flight.allowed", player.getAllowFlight());
        yaml.set("flight.flying", player.isFlying());
        yaml.set("flight.speed", player.getFlySpeed());
        yaml.set("walkSpeed", player.getWalkSpeed());

        List<java.util.Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffect e : player.getActivePotionEffects()) {
            effects.add(e.serialize());
        }
        yaml.set("effects", effects);

        File target = fileFor(player.getUniqueId());
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        try {
            yaml.save(tmp);
            Files.move(tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save sync data for " + player.getName() + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void load(Player player) {
        if (!enabled) return;
        if (player.hasPermission("smp.sync.bypass")) return;

        File f = fileFor(player.getUniqueId());
        if (!f.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);

        PlayerInventory inv = player.getInventory();
        inv.clear();

        List<?> contents = yaml.getList("inventory.contents");
        if (contents != null) {
            ItemStack[] arr = contents.stream()
                .map(o -> o instanceof ItemStack i ? i : null)
                .toArray(ItemStack[]::new);
            inv.setContents(arr);
        }
        List<?> armor = yaml.getList("inventory.armor");
        if (armor != null) {
            ItemStack[] arr = armor.stream()
                .map(o -> o instanceof ItemStack i ? i : null)
                .toArray(ItemStack[]::new);
            inv.setArmorContents(arr);
        }
        ItemStack offhand = yaml.getItemStack("inventory.offhand");
        if (offhand != null) inv.setItemInOffHand(offhand);
        inv.setHeldItemSlot(Math.max(0, Math.min(8, yaml.getInt("inventory.heldSlot", 0))));

        List<?> ender = yaml.getList("enderchest");
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
        if (gm != null) {
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

    public void saveAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) save(p);
    }
}
