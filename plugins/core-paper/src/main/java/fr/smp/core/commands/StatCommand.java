package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
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

        PlayerData d = plugin.players().loadOffline(target);
        if (d == null) { sender.sendMessage(Msg.err("Aucune donnée pour ce joueur.")); return true; }

        Player online = Bukkit.getPlayer(target);
        if (online != null) name = online.getName();

        String displayName = d.nickname() != null ? d.nickname() : name;

        List<Component> lines = new ArrayList<>();

        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        lines.add(MM.deserialize("<gradient:#a8edea:#fed6e3>  Statistiques</gradient> <dark_gray>»</dark_gray> <aqua>" + displayName + "</aqua>"));
        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        lines.add(MM.deserialize(""));
        lines.add(MM.deserialize("<yellow>⚔ Combat</yellow>"));
        lines.add(MM.deserialize("  <gray>Kills:</gray> <green>" + d.kills() + "</green>  <gray>Morts:</gray> <red>" + d.deaths() + "</red>"));
        double kdr = d.deaths() > 0 ? (double) d.kills() / d.deaths() : d.kills();
        lines.add(MM.deserialize("  <gray>KDR:</gray> <white>" + String.format("%.2f", kdr) + "</white>"));
        lines.add(MM.deserialize("  <gray>Kills du jour:</gray> <white>" + d.dailyKills() + "</white>"));

        lines.add(MM.deserialize(""));
        lines.add(MM.deserialize("<yellow>⏱ Temps de jeu</yellow>"));
        long totalSec = d.playtimeSec();
        long afkSec = 0;
        if (online != null && plugin.afk() != null) afkSec = plugin.afk().accumulatedAfkSec(online);
        long activeSec = Math.max(0, totalSec - afkSec);
        lines.add(MM.deserialize("  <gray>Total:</gray> <white>" + Msg.duration(totalSec) + "</white>"));
        lines.add(MM.deserialize("  <gray>Actif:</gray> <white>" + Msg.duration(activeSec) + "</white>"));
        if (afkSec > 0) {
            lines.add(MM.deserialize("  <gray>AFK:</gray> <white>" + Msg.duration(afkSec) + "</white>"));
        }
        if (online != null && plugin.afk() != null && plugin.afk().isAfk(online)) {
            lines.add(MM.deserialize("  <yellow>⚠ Actuellement AFK.</yellow>"));
        }

        lines.add(MM.deserialize(""));
        lines.add(MM.deserialize("<yellow>💰 Économie</yellow>"));
        lines.add(MM.deserialize("  <gray>Argent:</gray> <green>$" + String.format("%.2f", d.money()) + "</green>"));
        lines.add(MM.deserialize("  <gray>Saphirs:</gray> <aqua>" + d.shards() + "</aqua>"));

        if (d.teamId() != null) {
            TeamManager.Team team = plugin.teams().get(d.teamId());
            if (team != null) {
                lines.add(MM.deserialize(""));
                lines.add(MM.deserialize("<yellow>🏷 Team</yellow>"));
                String teamDisplay = team.color() != null ? team.color() + team.name() : team.name();
                lines.add(MM.deserialize("  <gray>Team:</gray> <white>" + teamDisplay + "</white>"));
                lines.add(MM.deserialize("  <gray>Tag:</gray> <white>[" + team.tag() + "]</white>"));
                lines.add(MM.deserialize("  <gray>Membres:</gray> <white>" + plugin.teams().memberCount(d.teamId()) + "</white>"));
            }
        }

        BountyManager BountyMgr = plugin.bounties();
        if (BountyMgr != null) {
            BountyManager.Bounty b = BountyMgr.get(target);
            if (b != null) {
                lines.add(MM.deserialize(""));
                lines.add(MM.deserialize("<yellow>🎯 Prime</yellow>"));
                lines.add(MM.deserialize("  <gray>Prime sur sa tête:</gray> <gold>$" + String.format("%.2f", b.amount()) + "</gold>"));
                lines.add(MM.deserialize("  <gray>Posée par:</gray> <white>" + b.lastIssuerName() + "</white>"));
            }
        }

        lines.add(MM.deserialize(""));
        lines.add(MM.deserialize("<yellow>📅 Divers</yellow>"));
        lines.add(MM.deserialize("  <gray>Première connexion:</gray> <white>" + DATE_FMT.format(new Date(d.firstJoin() * 1000L)) + "</white>"));
        lines.add(MM.deserialize("  <gray>Dernière activité:</gray> <white>" + DATE_FMT.format(new Date(d.lastSeen() * 1000L)) + "</white>"));

        if (online != null) {
            int ping = online.getPing();
            String color = ping < 80 ? "<green>" : ping < 180 ? "<yellow>" : "<red>";
            lines.add(MM.deserialize("  <gray>Ping:</gray> " + color + ping + "ms</>"));
            lines.add(MM.deserialize("  <gray>Statut:</gray> <green>En ligne</green>"));
        } else {
            lines.add(MM.deserialize("  <gray>Statut:</gray> <red>Hors-ligne</red>"));
        }

        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        for (Component line : lines) {
            sender.sendMessage(line);
        }

        return true;
    }
}
