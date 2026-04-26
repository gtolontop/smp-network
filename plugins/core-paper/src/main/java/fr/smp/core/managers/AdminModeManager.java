package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Toggle between "player" and "admin" pockets: two independent sets of
 * inventory / xp / stats / gamemode / flight per staff member, so a
 * survival player can switch to a clean creative loadout and back without
 * mixing their gear. State persists under plugins/SMPCore/admin-mode/&lt;uuid&gt;.yml
 * so a disconnect mid-toggle doesn't lose the saved pocket.
 */
public class AdminModeManager {

    private static final String MODE_PLAYER = "player";
    private static final String MODE_ADMIN = "admin";

    private final SMPCore plugin;
    private final File dir;

    public AdminModeManager(SMPCore plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "admin-mode");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create admin-mode dir: " + dir.getAbsolutePath());
        }
    }

    public boolean isAdmin(Player p) {
        File f = file(p.getUniqueId());
        if (!f.exists()) return false;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        return MODE_ADMIN.equalsIgnoreCase(cfg.getString("active", MODE_PLAYER));
    }

    /** Returns true if the player is now in admin mode, false if back in player mode. */
    public boolean toggle(Player p) {
        File f = file(p.getUniqueId());
        YamlConfiguration cfg = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        boolean currentlyAdmin = MODE_ADMIN.equalsIgnoreCase(cfg.getString("active", MODE_PLAYER));
        String currentKey = currentlyAdmin ? MODE_ADMIN : MODE_PLAYER;
        String targetKey = currentlyAdmin ? MODE_PLAYER : MODE_ADMIN;

        // Snapshot the pocket the player is leaving.
        saveState(p, cfg, currentKey);

        // Restore the pocket they're entering; first-ever switch to a mode
        // gets the default loadout for that mode.
        if (cfg.isConfigurationSection(targetKey)) {
            applyState(p, cfg.getConfigurationSection(targetKey));
        } else {
            applyDefaults(p, !currentlyAdmin);
        }

        cfg.set("active", targetKey);
        try {
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save admin-mode file for " + p.getName() + ": " + e.getMessage());
        }
        return !currentlyAdmin;
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }

    private void saveState(Player p, YamlConfiguration cfg, String key) {
        PlayerInventory inv = p.getInventory();
        cfg.set(key + ".inventory.contents", inv.getContents());
        cfg.set(key + ".inventory.armor", inv.getArmorContents());
        cfg.set(key + ".inventory.offhand", inv.getItemInOffHand());
        cfg.set(key + ".inventory.heldSlot", inv.getHeldItemSlot());

        cfg.set(key + ".enderchest", p.getEnderChest().getContents());

        cfg.set(key + ".xp.level", p.getLevel());
        cfg.set(key + ".xp.exp", p.getExp());
        cfg.set(key + ".xp.total", p.getTotalExperience());

        double maxHealth = maxHealth(p);
        cfg.set(key + ".stats.health", Math.min(p.getHealth(), maxHealth));
        cfg.set(key + ".stats.food", p.getFoodLevel());
        cfg.set(key + ".stats.saturation", p.getSaturation());

        cfg.set(key + ".gamemode", p.getGameMode().name());
        cfg.set(key + ".flight.allowed", p.getAllowFlight());
        cfg.set(key + ".flight.flying", p.isFlying());

        List<java.util.Map<String, Object>> effects = new java.util.ArrayList<>();
        for (PotionEffect e : p.getActivePotionEffects()) effects.add(e.serialize());
        cfg.set(key + ".effects", effects);
    }

    @SuppressWarnings("unchecked")
    private void applyState(Player p, ConfigurationSection sec) {
        PlayerInventory inv = p.getInventory();
        inv.clear();

        List<?> contents = sec.getList("inventory.contents");
        if (contents != null) {
            ItemStack[] arr = contents.stream()
                    .map(o -> o instanceof ItemStack i ? i : null)
                    .toArray(ItemStack[]::new);
            inv.setContents(arr);
        }
        List<?> armor = sec.getList("inventory.armor");
        if (armor != null) {
            ItemStack[] arr = armor.stream()
                    .map(o -> o instanceof ItemStack i ? i : null)
                    .toArray(ItemStack[]::new);
            inv.setArmorContents(arr);
        }
        ItemStack offhand = sec.getItemStack("inventory.offhand");
        inv.setItemInOffHand(offhand);
        inv.setHeldItemSlot(Math.max(0, Math.min(8, sec.getInt("inventory.heldSlot", 0))));

        List<?> ender = sec.getList("enderchest");
        p.getEnderChest().clear();
        if (ender != null) {
            ItemStack[] arr = ender.stream()
                    .map(o -> o instanceof ItemStack i ? i : null)
                    .toArray(ItemStack[]::new);
            p.getEnderChest().setContents(arr);
        }

        p.setLevel(sec.getInt("xp.level", 0));
        p.setExp((float) sec.getDouble("xp.exp", 0.0));
        p.setTotalExperience(sec.getInt("xp.total", 0));

        double maxHealth = maxHealth(p);
        double saved = sec.getDouble("stats.health", maxHealth);
        p.setHealth(Math.max(0.5, Math.min(saved, maxHealth)));
        p.setFoodLevel(sec.getInt("stats.food", 20));
        p.setSaturation((float) sec.getDouble("stats.saturation", 5.0));

        try {
            p.setGameMode(GameMode.valueOf(sec.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException ignored) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        p.setAllowFlight(sec.getBoolean("flight.allowed", false));
        p.setFlying(sec.getBoolean("flight.flying", false));

        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        List<?> effects = sec.getList("effects");
        if (effects != null) {
            for (Object raw : effects) {
                if (raw instanceof java.util.Map<?, ?> map) {
                    try {
                        p.addPotionEffect(new PotionEffect((java.util.Map<String, Object>) map));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void applyDefaults(Player p, boolean becomingAdmin) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        inv.setItemInOffHand(null);
        inv.setHeldItemSlot(0);
        p.getEnderChest().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());

        p.setLevel(0);
        p.setExp(0f);
        p.setTotalExperience(0);

        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(20f);

        if (becomingAdmin) {
            p.setGameMode(GameMode.CREATIVE);
            p.setAllowFlight(true);
            p.setFlying(true);
        } else {
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(false);
            p.setFlying(false);
        }
    }

    private double maxHealth(Player p) {
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}
