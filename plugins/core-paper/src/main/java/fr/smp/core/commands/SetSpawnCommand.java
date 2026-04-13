package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SetSpawnCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("smp.admin")) {
            player.sendMessage(mm.deserialize("<red>Permission refusée.</red>"));
            return true;
        }

        Location loc = player.getLocation();
        var config = plugin.getConfig();

        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", (double) loc.getYaw());
        config.set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();

        player.sendMessage(mm.deserialize(
                "<green>Spawn défini à ta position !</green> "
                + "<gray>(" + String.format("%.1f", loc.getX()) + ", "
                + String.format("%.1f", loc.getY()) + ", "
                + String.format("%.1f", loc.getZ()) + ")</gray>"));

        return true;
    }
}
