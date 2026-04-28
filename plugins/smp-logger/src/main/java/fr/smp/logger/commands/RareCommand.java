package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.dict.PlayerDict;
import fr.smp.logger.model.Action;
import fr.smp.logger.query.FilterParser;
import fr.smp.logger.util.RowFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * /rare [material|player] [time:7d] — fast lookup against rare_resources.
 *
 * Examples:
 *   /rare diamond_ore time:1d
 *   /rare spawner
 *   /rare player:Foo time:7d
 */
public class RareCommand implements CommandExecutor {

    private final SMPLogger plugin;

    public RareCommand(SMPLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Long since = System.currentTimeMillis() - 86_400_000L * 7L;
        Integer matFilter = null;
        Integer playerFilter = null;

        for (String a : args) {
            if (a.startsWith("time:")) since = System.currentTimeMillis() - FilterParser.parseDurationMs(a.substring(5));
            else if (a.startsWith("player:")) {
                PlayerDict.Entry pl = plugin.players().byName(a.substring(7));
                playerFilter = pl == null ? -1 : pl.id();
            } else {
                Material m = Material.matchMaterial(a);
                if (m != null) matFilter = plugin.materials().idOf(m);
            }
        }

        StringBuilder sql = new StringBuilder(
                "SELECT t, action, player_id, world_id, x, y, z, material_id, amount, fortune, silktouch, biome, spawner_type "
                        + "FROM rare_resources WHERE t >= ? ");
        if (matFilter != null) sql.append("AND material_id = ").append(matFilter).append(' ');
        if (playerFilter != null) sql.append("AND player_id = ").append(playerFilter).append(' ');
        sql.append("ORDER BY t DESC LIMIT 100");

        long sinceMs = since;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.db().reader();
                 PreparedStatement ps = c.prepareStatement(sql.toString())) {
                ps.setLong(1, sinceMs);
                try (ResultSet rs = ps.executeQuery()) {
                    sender.sendMessage(Component.text("─── rare lookup ───", NamedTextColor.GOLD));
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        long t = rs.getLong(1);
                        Action act = Action.of(rs.getInt(2));
                        String pn = name(rs.getInt(3));
                        String wn = plugin.worlds().nameOf(rs.getInt(4));
                        String mat = plugin.materials().nameOf(rs.getInt(8));
                        int amt = rs.getInt(9);
                        int fortune = rs.getInt(10);
                        int silk = rs.getInt(11);
                        String biome = rs.getString(12);
                        String stype = rs.getString(13);
                        StringBuilder line = new StringBuilder();
                        line.append(RowFormatter.formatAgo((System.currentTimeMillis() - t) / 1000)).append(' ');
                        line.append(pn).append(' ').append(act == null ? "?" : act.name().toLowerCase().replace('_', ' '))
                                .append(' ').append(amt).append("× ").append(mat);
                        line.append(" @ ").append(wn).append(' ').append(rs.getInt(5)).append(',').append(rs.getInt(6)).append(',').append(rs.getInt(7));
                        line.append(" [biome=").append(biome).append(']');
                        if (fortune > 0) line.append(" fortune=").append(fortune);
                        if (silk == 1) line.append(" silktouch");
                        if (stype != null && !stype.isEmpty()) line.append(" type=").append(stype);
                        sender.sendMessage(Component.text(line.toString(), NamedTextColor.LIGHT_PURPLE));
                    }
                    if (n == 0) sender.sendMessage(Component.text("No rare events match.", NamedTextColor.RED));
                }
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
        return true;
    }

    private String name(int id) {
        var e = plugin.players().byId(id);
        return e == null ? "#" + id : e.name();
    }
}
