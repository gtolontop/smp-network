package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OP-only cross-server player list. Uses the NetworkRoster snapshot pushed
 * by Velocity every 5s; falls back to local players if the roster hasn't
 * been received yet (e.g. right after a proxy restart).
 */
public class OnlineCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;

    public OnlineCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin") && !sender.hasPermission("smp.op")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        NetworkRoster roster = plugin.roster();
        List<NetworkRoster.Entry> all = roster != null ? roster.all() : List.of();

        if (all.isEmpty()) {
            // Fallback: uniquement serveur courant.
            for (Player p : Bukkit.getOnlinePlayers()) {
                String prefix = plugin.permissions() != null
                        ? plugin.permissions().prefixOf(p.getUniqueId())
                        : "";
                all = new ArrayList<>(all);
                all.add(new NetworkRoster.Entry(p.getName(), plugin.getServerType(), prefix == null ? "" : prefix));
            }
        }

        Map<String, List<NetworkRoster.Entry>> byServer = new LinkedHashMap<>();
        for (NetworkRoster.Entry e : all) {
            byServer.computeIfAbsent(e.server() == null ? "?" : e.server().toLowerCase(Locale.ROOT),
                    k -> new ArrayList<>()).add(e);
        }

        sender.sendMessage(MM.deserialize("<dark_gray>━━━━━━━ <gradient:#a8edea:#fed6e3>Réseau</gradient> <dark_gray>━━━━━━━</dark_gray>"));

        int total = 0;
        for (Map.Entry<String, List<NetworkRoster.Entry>> entry : byServer.entrySet()) {
            String srv = entry.getKey();
            List<NetworkRoster.Entry> list = entry.getValue();
            total += list.size();
            String color = switch (srv) {
                case "lobby" -> "<aqua>";
                case "survival" -> "<green>";
                default -> "<gold>";
            };
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) names.append("<dark_gray>, </dark_gray>");
                NetworkRoster.Entry e = list.get(i);
                String pfx = e.prefix() == null ? "" : e.prefix();
                names.append(pfx).append("<white>").append(escape(e.name())).append("</white>");
            }
            sender.sendMessage(MM.deserialize(
                    color + srv.toUpperCase(Locale.ROOT) + "</> <gray>(</gray><white>" + list.size()
                            + "</white><gray>)</gray> <dark_gray>»</dark_gray> " + names));
        }

        sender.sendMessage(MM.deserialize(
                "<gray>Total: <white>" + total + "</white> joueur(s)</gray>"));
        return true;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("<", "‹").replace(">", "›");
    }
}
