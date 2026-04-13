package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class JoinListener implements Listener {

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public JoinListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Suppress default join message — Velocity handles network-wide announcements
        event.joinMessage(null);

        if (plugin.isLobby()) {
            // Give compass for server selector
            if (plugin.getConfig().getBoolean("lobby.give-compass", true)) {
                giveCompass(player);
            }

            // Teleport to spawn (Folia-safe)
            Location spawn = getSpawnLocation();
            if (spawn != null) {
                player.teleportAsync(spawn);
            }
        }
    }

    private void giveCompass(Player player) {
        int slot = plugin.getConfig().getInt("lobby.compass-slot", 4);
        String compassName = plugin.getConfig().getString(
                "lobby.compass-name", "<gradient:#a8edea:#fed6e3>✦ Menu Serveurs ✦</gradient>");

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(mm.deserialize("<!italic>" + compassName));
        compass.setItemMeta(meta);

        player.getInventory().setItem(slot, compass);
    }

    private Location getSpawnLocation() {
        var config = plugin.getConfig();
        String worldName = config.getString("spawn.world", "world");
        var world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Spawn world '" + worldName + "' not found.");
            return null;
        }

        return new Location(
                world,
                config.getDouble("spawn.x", 0.5),
                config.getDouble("spawn.y", 100.0),
                config.getDouble("spawn.z", 0.5),
                (float) config.getDouble("spawn.yaw", 0.0),
                (float) config.getDouble("spawn.pitch", 0.0)
        );
    }
}
