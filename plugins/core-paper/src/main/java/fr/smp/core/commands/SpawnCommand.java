package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final SMPCore plugin;

    public SpawnCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (plugin.combat() != null && plugin.combat().isTagged(p)) {
            p.sendMessage(Msg.err("Tu es en combat. Attends <white>" +
                    plugin.combat().remainingSec(p) + "s</white>."));
            return true;
        }
        if (plugin.isLobby()) {
            Location l = plugin.spawns().hub();
            if (l == null) {
                p.sendMessage(Msg.err("Hub non configuré. Utilise <yellow>/sethubspawn</yellow> ici."));
                return true;
            }
            plugin.getLogger().info("[SPAWN] " + p.getName() + " -> hub "
                    + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ()
                    + " (" + l.getWorld().getName() + ")");
            p.teleportAsync(l).thenAccept(ok -> {
                if (ok) p.sendMessage(Msg.ok("<green>Téléporté au hub.</green>"));
            });
        } else {
            plugin.getLogger().info("[SPAWN] " + p.getName() + " -> lobby (transfer depuis " + plugin.getServerType() + ")");
            p.sendMessage(Msg.info("<aqua>Connexion au lobby...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, "lobby");
        }
        return true;
    }
}
