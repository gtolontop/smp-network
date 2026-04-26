package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * /send — Admin command to transfer players between servers.
 *
 *   /send <player> <server>           — send a specific player
 *   /send all <server>                — send ALL network players
 *   /send allhere <server>            — send all players on THIS server
 *   /send server <source> <dest>      — send all players from source server to dest
 *   /send me <server>                 — send yourself
 *   /send                             — show help
 */
public class SendCommand implements CommandExecutor, TabCompleter {

    private static final List<String> KNOWN_SERVERS = List.of("lobby", "survival", "ptr");

    private final SMPCore plugin;

    public SendCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "all" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.err("/send all <serveur>"));
                    return true;
                }
                return handleSendAll(sender, args[1]);
            }
            case "allhere" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.err("/send allhere <serveur>"));
                    return true;
                }
                return handleSendAllHere(sender, args[1]);
            }
            case "server" -> {
                if (args.length < 3) {
                    sender.sendMessage(Msg.err("/send server <source> <destination>"));
                    return true;
                }
                return handleSendServer(sender, args[1], args[2]);
            }
            case "me" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Joueurs uniquement.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Msg.err("/send me <serveur>"));
                    return true;
                }
                return handleSendMe(p, args[1]);
            }
            default -> {
                // /send <player> <server>
                if (args.length < 2) {
                    sender.sendMessage(Msg.err("/send <joueur> <serveur>"));
                    return true;
                }
                return handleSendPlayer(sender, args[0], args[1]);
            }
        }
    }

    // ---- sub-command handlers ----

    private boolean handleSendPlayer(CommandSender sender, String playerName, String serverName) {
        // Try local first
        Player local = Bukkit.getPlayerExact(playerName);
        if (local != null) {
            if (serverName.equalsIgnoreCase(plugin.getServerType())) {
                sender.sendMessage(Msg.err("<white>" + local.getName() + "</white> est déjà sur <white>" + serverName + "</white>."));
                return true;
            }
            plugin.getMessageChannel().sendTransfer(local, serverName);
            sender.sendMessage(Msg.ok("<aqua>" + local.getName() + "</aqua> <gray>→</gray> <white>" + serverName + "</white>"));
            return true;
        }

        // Try network roster
        NetworkRoster.Entry entry = plugin.roster() != null ? plugin.roster().get(playerName) : null;
        if (entry == null) {
            sender.sendMessage(Msg.err("Joueur introuvable : <white>" + playerName + "</white>"));
            return true;
        }
        if (serverName.equalsIgnoreCase(entry.server())) {
            sender.sendMessage(Msg.err("<white>" + entry.name() + "</white> est déjà sur <white>" + serverName + "</white>."));
            return true;
        }
        plugin.getMessageChannel().sendTransferByName(entry.name(), serverName);
        sender.sendMessage(Msg.ok("<aqua>" + entry.name() + "</aqua> <gray>→</gray> <white>" + serverName + "</white> <dark_gray>(distant)</dark_gray>"));
        return true;
    }

    private boolean handleSendAll(CommandSender sender, String serverName) {
        // Collect all unique player names from the network roster + local
        Set<String> sent = new HashSet<>();
        int count = 0;

        // Local players first (direct transfer, faster)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (serverName.equalsIgnoreCase(plugin.getServerType())) continue; // already here
            plugin.getMessageChannel().sendTransfer(p, serverName);
            sent.add(p.getName().toLowerCase());
            count++;
        }

        // Remote players via roster
        if (plugin.roster() != null) {
            for (NetworkRoster.Entry entry : plugin.roster().all()) {
                if (sent.contains(entry.name().toLowerCase())) continue;
                if (serverName.equalsIgnoreCase(entry.server())) continue; // already on target
                plugin.getMessageChannel().sendTransferByName(entry.name(), serverName);
                count++;
            }
        }

        if (count == 0) {
            sender.sendMessage(Msg.info("<yellow>Aucun joueur à envoyer.</yellow>"));
        } else {
            sender.sendMessage(Msg.ok("<green>" + count + "</green> joueur(s) envoyé(s) vers <white>" + serverName + "</white>."));
        }
        return true;
    }

    private boolean handleSendAllHere(CommandSender sender, String serverName) {
        if (serverName.equalsIgnoreCase(plugin.getServerType())) {
            sender.sendMessage(Msg.err("Tu es déjà sur <white>" + serverName + "</white>."));
            return true;
        }

        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getMessageChannel().sendTransfer(p, serverName);
            count++;
        }

        if (count == 0) {
            sender.sendMessage(Msg.info("<yellow>Aucun joueur sur ce serveur.</yellow>"));
        } else {
            sender.sendMessage(Msg.ok("<green>" + count + "</green> joueur(s) de <white>" + plugin.getServerType() +
                    "</white> envoyé(s) vers <white>" + serverName + "</white>."));
        }
        return true;
    }

    private boolean handleSendServer(CommandSender sender, String sourceServer, String destServer) {
        if (sourceServer.equalsIgnoreCase(destServer)) {
            sender.sendMessage(Msg.err("Source et destination sont identiques."));
            return true;
        }

        int count = 0;

        // If source is this server, we can transfer directly
        if (sourceServer.equalsIgnoreCase(plugin.getServerType())) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getMessageChannel().sendTransfer(p, destServer);
                count++;
            }
        } else if (plugin.roster() != null) {
            // Remote server — use roster to get players on that server
            for (NetworkRoster.Entry entry : plugin.roster().onServer(sourceServer)) {
                plugin.getMessageChannel().sendTransferByName(entry.name(), destServer);
                count++;
            }
        }

        if (count == 0) {
            sender.sendMessage(Msg.info("<yellow>Aucun joueur sur <white>" + sourceServer + "</white>.</yellow>"));
        } else {
            sender.sendMessage(Msg.ok("<green>" + count + "</green> joueur(s) de <white>" + sourceServer +
                    "</white> envoyé(s) vers <white>" + destServer + "</white>."));
        }
        return true;
    }

    private boolean handleSendMe(Player p, String serverName) {
        if (serverName.equalsIgnoreCase(plugin.getServerType())) {
            p.sendMessage(Msg.err("Tu es déjà sur <white>" + serverName + "</white>."));
            return true;
        }
        plugin.getMessageChannel().sendTransfer(p, serverName);
        p.sendMessage(Msg.ok("Transfert vers <white>" + serverName + "</white>..."));
        return true;
    }

    // ---- help ----

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Msg.info("<gradient:#ffb347:#ff6f61>Send</gradient> <dark_gray>— Transfert de joueurs</dark_gray>"));
        sender.sendMessage(Msg.mm("  <white>/send <joueur> <serveur></white> <dark_gray>—</dark_gray> <gray>Envoyer un joueur</gray>"));
        sender.sendMessage(Msg.mm("  <white>/send me <serveur></white> <dark_gray>—</dark_gray> <gray>S'envoyer soi-même</gray>"));
        sender.sendMessage(Msg.mm("  <white>/send all <serveur></white> <dark_gray>—</dark_gray> <gray>Envoyer tout le réseau</gray>"));
        sender.sendMessage(Msg.mm("  <white>/send allhere <serveur></white> <dark_gray>—</dark_gray> <gray>Envoyer ce serveur</gray>"));
        sender.sendMessage(Msg.mm("  <white>/send server <source> <dest></white> <dark_gray>—</dark_gray> <gray>Transférer un serveur entier</gray>"));
    }

    // ---- tab completion ----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();

        if (args.length == 1) {
            // Sub-commands + player names
            List<String> options = new ArrayList<>(List.of("all", "allhere", "server", "me"));
            options.addAll(networkPlayerNames());
            return filterPrefix(options, args[0]);
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "all", "allhere", "me" -> {
                if (args.length == 2) return filterPrefix(serverNames(), args[1]);
            }
            case "server" -> {
                if (args.length == 2) return filterPrefix(serverNames(), args[1]);
                if (args.length == 3) return filterPrefix(serverNames(), args[2]);
            }
            default -> {
                // /send <player> <server>
                if (args.length == 2) return filterPrefix(serverNames(), args[1]);
            }
        }

        return List.of();
    }

    // ---- helpers ----

    private List<String> serverNames() {
        Set<String> servers = new HashSet<>(KNOWN_SERVERS);
        // Add any servers seen in the roster (dynamic)
        if (plugin.roster() != null) {
            for (NetworkRoster.Entry e : plugin.roster().all()) {
                servers.add(e.server().toLowerCase());
            }
        }
        return new ArrayList<>(servers);
    }

    private List<String> networkPlayerNames() {
        Set<String> names = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        if (plugin.roster() != null) {
            for (NetworkRoster.Entry e : plugin.roster().all()) names.add(e.name());
        }
        return new ArrayList<>(names);
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(lower)) out.add(s);
        }
        return out;
    }
}
