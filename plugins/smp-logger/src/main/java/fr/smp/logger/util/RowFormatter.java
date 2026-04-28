package fr.smp.logger.util;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.query.LookupEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Duration;
import java.time.Instant;

/** Pretty-prints a {@link LookupEngine.Row} as an Adventure component. */
public final class RowFormatter {

    private final SMPLogger plugin;

    public RowFormatter(SMPLogger plugin) {
        this.plugin = plugin;
    }

    public Component format(LookupEngine.Row r) {
        String actor = nameOf(r.actorId(), "console/world");
        String world = plugin.worlds().nameOf(r.worldId());
        String mat = plugin.materials().nameOf(r.materialId());
        String text = r.textId() == 0 ? null : plugin.strings().textOf(r.textId());
        long agoSec = (System.currentTimeMillis() - r.timeMs()) / 1000L;
        String ago = formatAgo(agoSec);

        Component c = Component.text(ago + " ", NamedTextColor.GRAY)
                .append(Component.text(actor, NamedTextColor.YELLOW))
                .append(Component.text(" " + r.action().name().toLowerCase().replace('_', ' ') + " ", NamedTextColor.AQUA))
                .append(Component.text(mat == null ? "?" : mat, NamedTextColor.GREEN));

        if (r.amount() > 1) {
            c = c.append(Component.text(" x" + r.amount(), NamedTextColor.GOLD));
        }
        if (world != null) {
            c = c.append(Component.text(" @ " + world + " "
                    + r.x() + "," + r.y() + "," + r.z(), NamedTextColor.DARK_GRAY));
        }
        if (text != null && !text.isEmpty()) {
            String shown = text.length() > 80 ? text.substring(0, 80) + "…" : text;
            c = c.append(Component.text(" \"" + shown + "\"", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, true));
        }
        if (r.itemHash() != null) {
            c = c.append(Component.text(" [precious]", NamedTextColor.LIGHT_PURPLE));
        }
        return c;
    }

    private String nameOf(int id, String fallback) {
        if (id == 0) return fallback;
        var e = plugin.players().byId(id);
        return e == null ? "#" + id : e.name();
    }

    public static String formatAgo(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        return (seconds / 86400) + "d";
    }

    public static String formatInstant(long ms) {
        return Instant.ofEpochMilli(ms).toString();
    }

    public static String formatDuration(long ms) {
        Duration d = Duration.ofMillis(ms);
        long days = d.toDays();
        long hours = d.minusDays(days).toHours();
        long mins = d.minusDays(days).minusHours(hours).toMinutes();
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        sb.append(mins).append("m");
        return sb.toString();
    }
}
