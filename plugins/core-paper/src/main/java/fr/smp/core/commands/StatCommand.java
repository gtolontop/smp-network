package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.gui.StatGUI;
import fr.smp.core.managers.BountyManager;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class StatCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final SMPCore plugin;

    public StatCommand(SMPCore plugin) {
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
            sender.sendMessage(Msg.err("/stat <joueur>"));
            return true;
        }

        if (sender instanceof Player viewer) {
            new StatGUI(plugin).open(viewer, target, name);
            return true;
        }

        // Console fallback: text output
        printText(sender, target, name);
        return true;
    }

    private void printText(CommandSender sender, UUID target, String name) {
        PlayerData d = plugin.players().loadOffline(target);
        if (d == null) { sender.sendMessage(Msg.err("Aucune donnée pour ce joueur.")); return; }

        Player online = Bukkit.getPlayer(target);
        if (online != null) name = online.getName();
        String displayName = d.nickname() != null ? d.nickname() : name;

        List<Component> lines = new ArrayList<>();
        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        lines.add(MM.deserialize("<gradient:#a8edea:#fed6e3>  Statistiques</gradient> <dark_gray>»</dark_gray> <aqua>" + displayName + "</aqua>"));
        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        double kdr = d.deaths() > 0 ? (double) d.kills() / d.deaths() : d.kills();
        lines.add(MM.deserialize("<yellow>⚔ Combat</yellow>"));
        lines.add(MM.deserialize("  <gray>Kills:</gray> <green>" + d.kills() + "</green>  <gray>Morts:</gray> <red>" + d.deaths() + "</red>  <gray>KDR:</gray> <white>" + String.format("%.2f", kdr) + "</white>"));

        long totalSec = d.playtimeSec();
        lines.add(MM.deserialize("<yellow>⏱ Playtime:</yellow> <white>" + Msg.duration(totalSec) + "</white>"));

        lines.add(MM.deserialize("<yellow>💰 Argent:</yellow> <green>$" + String.format("%.2f", d.money()) + "</green>  <yellow>Saphirs:</yellow> <aqua>" + d.shards() + "</aqua>"));

        if (d.teamId() != null) {
            TeamManager.Team team = plugin.teams().get(d.teamId());
            if (team != null) {
                lines.add(MM.deserialize("<yellow>🏷 Team:</yellow> <white>" + team.name() + " [" + team.tag() + "]</white>"));
            }
        }

        BountyManager mgr = plugin.bounties();
        if (mgr != null) {
            BountyManager.Bounty b = mgr.get(target);
            if (b != null) {
                lines.add(MM.deserialize("<yellow>🎯 Prime:</yellow> <gold>$" + String.format("%.2f", b.amount()) + "</gold>"));
            }
        }

        lines.add(MM.deserialize("<gray>Première: <white>" + DATE_FMT.format(new Date(d.firstJoin() * 1000L)) + "</white> Dernière: <white>" + DATE_FMT.format(new Date(d.lastSeen() * 1000L)) + "</white></gray>"));
        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        for (Component line : lines) sender.sendMessage(line);
    }
}
