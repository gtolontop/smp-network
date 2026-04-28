package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.dict.PlayerDict;
import fr.smp.logger.util.RowFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SeenCommand implements CommandExecutor {

    private final SMPLogger plugin;

    public SeenCommand(SMPLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /seen <player>", NamedTextColor.GRAY));
            return true;
        }
        PlayerDict.Entry e = plugin.players().byName(args[0]);
        if (e == null) {
            sender.sendMessage(Component.text("Unknown player.", NamedTextColor.RED));
            return true;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.db().reader();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT id, ip, brand, locale, version, joined_at, left_at, "
                                 + "last_world_id, last_x, last_y, last_z, kicked, quit_reason "
                                 + "FROM sessions WHERE player_id = ? ORDER BY joined_at DESC LIMIT 5")) {
                ps.setInt(1, e.id());
                try (ResultSet rs = ps.executeQuery()) {
                    sender.sendMessage(Component.text("─── /seen " + e.name() + " ───", NamedTextColor.GOLD));
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        long join = rs.getLong("joined_at");
                        long left = rs.getLong("left_at");
                        boolean online = left == 0;
                        long durMs = (online ? System.currentTimeMillis() : left) - join;
                        String wn = rs.getInt("last_world_id") == 0 ? "?" : plugin.worlds().nameOf(rs.getInt("last_world_id"));
                        sender.sendMessage(Component.text(
                                "  [" + (online ? "ONLINE" : RowFormatter.formatAgo((System.currentTimeMillis() - left) / 1000) + " ago")
                                        + "] " + RowFormatter.formatDuration(durMs)
                                        + " — " + rs.getString("brand") + " " + rs.getString("version")
                                        + " — IP " + rs.getString("ip")
                                        + " — last: " + wn + " " + rs.getInt("last_x") + "," + rs.getInt("last_y") + "," + rs.getInt("last_z"),
                                online ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                    }
                    if (n == 0) sender.sendMessage(Component.text("No session history.", NamedTextColor.RED));
                }
            } catch (SQLException ex) {
                sender.sendMessage(Component.text("DB error: " + ex.getMessage(), NamedTextColor.RED));
            }
        });
        return true;
    }
}
