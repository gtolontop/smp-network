package fr.smp.logger.backup;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.util.Compression;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Periodic, async, compressed YAML snapshot of every online player's full
 * vital state (inventory + ender chest + xp + health + hunger + effects + loc).
 * Stored in inv_snapshots with DEFLATEd YAML so a 36-slot inventory snapshot
 * is typically ~1-3 KB on disk.
 *
 * Replaces the old InventoryHistoryManager flow but uses the logger DB +
 * compression to cut storage ~5x.
 */
public class InventorySnapshotter {

    private final SMPLogger plugin;
    private final boolean enabled;
    private final int keepPerPlayer;
    private final long intervalTicks;

    public InventorySnapshotter(SMPLogger plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("backup.inv-snapshots.enabled", true);
        this.keepPerPlayer = Math.max(5, plugin.getConfig().getInt("backup.inv-snapshots.keep-per-player", 50));
        int minutes = Math.max(1, plugin.getConfig().getInt("backup.inv-snapshots.interval-minutes", 5));
        this.intervalTicks = minutes * 60L * 20L;
    }

    public boolean enabled() { return enabled; }
    public long intervalTicks() { return intervalTicks; }

    public void snapshotAll(String source) {
        if (!enabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) snapshot(p, source);
    }

    /** Captures on the main thread (Bukkit state), persists async. */
    public void snapshot(Player p, String source) {
        if (!enabled) return;
        try {
            YamlConfiguration y = new YamlConfiguration();
            PlayerInventory inv = p.getInventory();
            y.set("contents", listToYaml(inv.getContents()));
            y.set("armor", listToYaml(inv.getArmorContents()));
            y.set("offhand", listToYaml(inv.getExtraContents()));
            y.set("ender", listToYaml(p.getEnderChest().getContents()));
            y.set("xp_total", p.getTotalExperience());
            y.set("xp_level", p.getLevel());
            y.set("xp_progress", p.getExp());
            y.set("health", p.getHealth());
            y.set("hunger", p.getFoodLevel());
            y.set("saturation", p.getSaturation());
            y.set("gamemode", p.getGameMode().name());
            y.set("world", p.getWorld().getName());
            y.set("x", p.getLocation().getX());
            y.set("y", p.getLocation().getY());
            y.set("z", p.getLocation().getZ());
            List<String> effects = new ArrayList<>();
            for (PotionEffect e : p.getActivePotionEffects()) {
                effects.add(e.getType().getKey() + ":" + e.getAmplifier() + ":" + e.getDuration());
            }
            y.set("effects", effects);
            String yaml = y.saveToString();

            UUID uuid = p.getUniqueId();
            String name = p.getName();
            String server = plugin.getServer().getName();
            byte[] compressed = Compression.deflate(yaml.getBytes(StandardCharsets.UTF_8));
            int playerId = plugin.players().idOf(uuid, name);
            long now = System.currentTimeMillis();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> persist(playerId, source, server, now, compressed));
        } catch (Throwable t) {
            plugin.getLogger().warning("Inv snapshot failed for " + p.getName() + ": " + t.getMessage());
        }
    }

    private void persist(int playerId, String source, String server, long now, byte[] payload) {
        try (Connection c = plugin.db().writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inv_snapshots(player_id, source, server, created_at, yaml_zlib) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, playerId);
            ps.setString(2, source);
            ps.setString(3, server);
            ps.setLong(4, now);
            ps.setBytes(5, payload);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Inv snapshot persist failed: " + e.getMessage());
            return;
        }
        // Rotate: keep only N most recent per player.
        try (Connection c = plugin.db().writer();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM inv_snapshots WHERE player_id = ? AND id NOT IN ("
                             + "SELECT id FROM inv_snapshots WHERE player_id = ? ORDER BY created_at DESC LIMIT ?)")) {
            ps.setInt(1, playerId);
            ps.setInt(2, playerId);
            ps.setInt(3, keepPerPlayer);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static List<java.util.Map<String, Object>> listToYaml(ItemStack[] items) {
        List<java.util.Map<String, Object>> out = new ArrayList<>(items.length);
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null || it.getType().isAir()) continue;
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("slot", i);
            m.put("item", it.serialize());
            out.add(m);
        }
        return out;
    }
}
