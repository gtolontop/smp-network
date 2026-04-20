package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SaphirCommand implements CommandExecutor {

    private final SMPCore plugin;

    public SaphirCommand(SMPCore plugin) {
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
            sender.sendMessage(Msg.err("/saphir <joueur>")); return true;
        }
        PlayerData d = plugin.players().loadOffline(target);
        if (d == null) { sender.sendMessage(Msg.err("Aucune donnée.")); return true; }
        long total = d.shards();
        long perMin = plugin.getConfig().getLong("economy.shards-per-minute", 1);
        sender.sendMessage(Msg.info("<aqua><bold>◆ Saphirs</bold></aqua> <gray>·</gray> <white>" + name + "</white>"));
        sender.sendMessage(Msg.mm("  <gray>Solde:</gray> <aqua>" + total + "</aqua>"));
        sender.sendMessage(Msg.mm("  <gray>Gain:</gray> <aqua>" + perMin + "</aqua> <gray>/ min de jeu</gray>"));
        return true;
    }
}
