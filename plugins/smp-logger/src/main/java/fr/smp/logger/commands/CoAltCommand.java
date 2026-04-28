package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.dict.PlayerDict;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Finds players who shared an IP with the target. */
public class CoAltCommand implements CommandExecutor {

    private final SMPLogger plugin;

    public CoAltCommand(SMPLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /coalt <player|ip>", NamedTextColor.GRAY));
            return true;
        }
        String input = args[0];
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> ips = new LinkedHashSet<>();
            // If input looks like an IP, use directly; else resolve player → his IPs.
            if (input.contains(".") || input.contains(":")) {
                ips.add(input);
            } else {
                PlayerDict.Entry pl = plugin.players().byName(input);
                if (pl == null) {
                    sender.sendMessage(Component.text("Unknown player.", NamedTextColor.RED));
                    return;
                }
                try (Connection c = plugin.db().reader();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT DISTINCT ip FROM sessions WHERE player_id = ? AND ip != 'unknown' "
                                     + "ORDER BY joined_at DESC LIMIT 10")) {
                    ps.setInt(1, pl.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) ips.add(rs.getString(1));
                    }
                } catch (SQLException e) {
                    sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
                    return;
                }
            }
            if (ips.isEmpty()) {
                sender.sendMessage(Component.text("No IPs found for " + input, NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text("─── alts sharing IP with " + input + " ───", NamedTextColor.GOLD));
            for (String ip : ips) {
                try (Connection c = plugin.db().reader();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT DISTINCT s.player_id, MAX(s.joined_at) as last "
                                     + "FROM sessions s WHERE s.ip = ? GROUP BY s.player_id ORDER BY last DESC LIMIT 25")) {
                    ps.setString(1, ip);
                    try (ResultSet rs = ps.executeQuery()) {
                        sender.sendMessage(Component.text("  IP " + ip + ":", NamedTextColor.YELLOW));
                        while (rs.next()) {
                            int id = rs.getInt(1);
                            var e = plugin.players().byId(id);
                            sender.sendMessage(Component.text("    - " + (e == null ? "#" + id : e.name()),
                                    NamedTextColor.AQUA));
                        }
                    }
                } catch (SQLException ex) {
                    sender.sendMessage(Component.text("    [DB error]", NamedTextColor.RED));
                }
            }
        });
        return true;
    }
}
