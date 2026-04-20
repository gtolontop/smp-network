package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlaytimeCommand implements CommandExecutor {

    private final SMPCore plugin;

    public PlaytimeCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        UUID target;
        String name;
        if (args.length > 0) {
            target = plugin.players().resolveUuid(args[0]);
            name = args[0];
            if (target == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return true; }
        } else if (sender instanceof Player p) {
            target = p.getUniqueId();
            name = p.getName();
        } else {
            sender.sendMessage(Msg.err("/playtime <joueur>"));
            return true;
        }
        PlayerData d = plugin.players().loadOffline(target);
        if (d == null) { sender.sendMessage(Msg.err("Aucune donnée.")); return true; }
        long totalSec = d.playtimeSec();
        long afkSec = 0;
        Player online = Bukkit.getPlayer(target);
        if (online != null && plugin.afk() != null) afkSec = plugin.afk().accumulatedAfkSec(online);
        long activeSec = Math.max(0, totalSec - afkSec);
        sender.sendMessage(Msg.info("<gold><bold>Playtime</bold></gold> <gray>·</gray> <aqua>" + name + "</aqua>"));
        sender.sendMessage(Msg.mm("  <gray>Total:</gray> <white>" + Msg.duration(totalSec) + "</white>"));
        sender.sendMessage(Msg.mm("  <gray>Actif:</gray> <white>" + Msg.duration(activeSec) + "</white>"));
        if (afkSec > 0) sender.sendMessage(Msg.mm("  <gray>AFK (session):</gray> <white>" + Msg.duration(afkSec) + "</white>"));
        if (online != null && plugin.afk() != null && plugin.afk().isAfk(online)) {
            sender.sendMessage(Msg.mm("  <yellow>⚠ Actuellement AFK.</yellow>"));
        }
        return true;
    }
}
