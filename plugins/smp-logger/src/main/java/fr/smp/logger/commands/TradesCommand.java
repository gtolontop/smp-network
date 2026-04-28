package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.dict.PlayerDict;
import fr.smp.logger.model.Action;
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

public class TradesCommand implements CommandExecutor {

    private final SMPLogger plugin;

    public TradesCommand(SMPLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /trades <player> [item]", NamedTextColor.GRAY));
            return true;
        }
        PlayerDict.Entry pl = plugin.players().byName(args[0]);
        if (pl == null) { sender.sendMessage(Component.text("Unknown player.", NamedTextColor.RED)); return true; }
        Integer itemId = null;
        if (args.length > 1) {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(args[1]);
            if (m != null) itemId = plugin.materials().idOf(m);
        }
        final Integer itemFilter = itemId;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT t, from_player, to_player, method, material_id, amount, world_id, x, y, z "
                    + "FROM trades WHERE (from_player = ? OR to_player = ?) "
                    + (itemFilter != null ? "AND material_id = ? " : "")
                    + "ORDER BY t DESC LIMIT 50";
            try (Connection c = plugin.db().reader();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, pl.id());
                ps.setInt(2, pl.id());
                if (itemFilter != null) ps.setInt(3, itemFilter);
                try (ResultSet rs = ps.executeQuery()) {
                    sender.sendMessage(Component.text("─── trades for " + pl.name() + " ───", NamedTextColor.GOLD));
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        long t = rs.getLong(1);
                        int from = rs.getInt(2), to = rs.getInt(3);
                        Action a = Action.of(rs.getInt(4));
                        String mat = plugin.materials().nameOf(rs.getInt(5));
                        int amt = rs.getInt(6);
                        String wn = plugin.worlds().nameOf(rs.getInt(7));
                        String fromName = name(from), toName = name(to);
                        sender.sendMessage(Component.text(
                                RowFormatter.formatAgo((System.currentTimeMillis() - t) / 1000) + " "
                                        + fromName + " → " + toName + " "
                                        + amt + "× " + mat
                                        + " via " + (a == null ? "?" : a.name())
                                        + " @ " + wn + " " + rs.getInt(8) + "," + rs.getInt(9) + "," + rs.getInt(10),
                                NamedTextColor.AQUA));
                    }
                    if (n == 0) sender.sendMessage(Component.text("No trades.", NamedTextColor.RED));
                }
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
        return true;
    }

    private String name(int id) {
        if (id == 0) return "?";
        var e = plugin.players().byId(id);
        return e == null ? "#" + id : e.name();
    }
}
