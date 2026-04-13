package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SpawnCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Location spawn = getSpawnLocation();
        if (spawn == null) {
            player.sendMessage(mm.deserialize("<red>Spawn non configuré.</red>"));
            return true;
        }

        // Folia-safe async teleport
        player.teleportAsync(spawn).thenAccept(success -> {
            if (success) {
                player.sendMessage(mm.deserialize("<green>Téléporté au spawn !</green>"));
            } else {
                player.sendMessage(mm.deserialize("<red>Échec de la téléportation.</red>"));
            }
        });

        return true;
    }

    private Location getSpawnLocation() {
        var config = plugin.getConfig();
        String worldName = config.getString("spawn.world", "world");
        World world = plugin.getServer().getWorld(worldName);
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
