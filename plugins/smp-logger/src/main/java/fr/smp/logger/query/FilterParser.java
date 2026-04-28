package fr.smp.logger.query;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * DSL: "player:foo action:break item:diamond_sword time:1d radius:10 world:survival page:2"
 *
 * Time tokens: 1s, 30m, 6h, 2d, 1w (sums allowed: "1d6h").
 * Material/Action are resolved via Bukkit registry / Action enum.
 */
public final class FilterParser {

    private final SMPLogger plugin;

    public FilterParser(SMPLogger plugin) {
        this.plugin = plugin;
    }

    public LookupFilter parse(Player runner, String[] args) {
        LookupFilter f = new LookupFilter();
        long now = System.currentTimeMillis();
        for (String raw : args) {
            int i = raw.indexOf(':');
            if (i < 0) continue;
            String k = raw.substring(0, i).toLowerCase(Locale.ROOT);
            String v = raw.substring(i + 1);
            try {
                switch (k) {
                    case "p", "player" -> f.playerId = playerId(v);
                    case "a", "action" -> f.action = parseAction(v);
                    case "i", "item", "block", "material" -> f.materialId = materialId(v);
                    case "t", "time", "since" -> f.sinceMs = now - parseDurationMs(v);
                    case "before", "until" -> f.untilMs = now - parseDurationMs(v);
                    case "w", "world" -> f.worldId = worldId(v, runner);
                    case "x" -> f.x = Integer.parseInt(v);
                    case "y" -> f.y = Integer.parseInt(v);
                    case "z" -> f.z = Integer.parseInt(v);
                    case "r", "radius" -> f.radius = Integer.parseInt(v);
                    case "limit" -> f.limit = Math.min(500, Math.max(1, Integer.parseInt(v)));
                    case "page" -> f.page = Math.max(1, Integer.parseInt(v));
                    case "near" -> {
                        if (runner != null) {
                            f.worldId = plugin.worlds().idOf(runner.getWorld());
                            f.x = runner.getLocation().getBlockX();
                            f.y = runner.getLocation().getBlockY();
                            f.z = runner.getLocation().getBlockZ();
                            f.radius = Integer.parseInt(v);
                        }
                    }
                    default -> {}
                }
            } catch (NumberFormatException ignored) {}
        }
        return f;
    }

    private Integer playerId(String name) {
        var e = plugin.players().byName(name);
        return e == null ? -1 : e.id();
    }

    private Action parseAction(String s) {
        try { return Action.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            // Friendly aliases.
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "break" -> Action.BLOCK_BREAK;
                case "place" -> Action.BLOCK_PLACE;
                case "kill" -> Action.ENTITY_KILL;
                case "death" -> Action.PLAYER_DEATH;
                case "chat" -> Action.CHAT;
                case "cmd", "command" -> Action.COMMAND;
                case "join" -> Action.SESSION_JOIN;
                case "quit", "leave" -> Action.SESSION_QUIT;
                case "drop" -> Action.DROP_ITEM;
                case "pickup" -> Action.PICKUP_ITEM;
                case "open" -> Action.CONTAINER_OPEN;
                case "close" -> Action.CONTAINER_CLOSE;
                case "insert" -> Action.CONTAINER_INSERT;
                case "take" -> Action.CONTAINER_TAKE;
                case "tp" -> Action.TELEPORT;
                case "trade" -> Action.TRADE_DROP_PICKUP;
                case "rare" -> Action.RARE_BREAK;
                case "spawner" -> Action.SPAWNER_BREAK;
                default -> null;
            };
        }
    }

    private Integer materialId(String s) {
        Material m = Material.matchMaterial(s);
        if (m != null) return plugin.materials().idOf(m);
        // Otherwise treat as raw name (covers EntityType keys for kill lookups).
        int id = plugin.materials().idOf(s.toUpperCase(Locale.ROOT));
        return id == 0 ? -1 : id;
    }

    private Integer worldId(String s, Player runner) {
        if ("here".equalsIgnoreCase(s) && runner != null) return plugin.worlds().idOf(runner.getWorld());
        int id = plugin.worlds().idOf(s);
        return id == 0 ? -1 : id;
    }

    public static long parseDurationMs(String s) {
        long total = 0;
        long acc = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) acc = acc * 10 + (c - '0');
            else {
                long unit = switch (Character.toLowerCase(c)) {
                    case 's' -> 1000L;
                    case 'm' -> 60_000L;
                    case 'h' -> 3_600_000L;
                    case 'd' -> 86_400_000L;
                    case 'w' -> 7L * 86_400_000L;
                    default -> 0L;
                };
                total += acc * unit;
                acc = 0;
            }
        }
        if (acc > 0) total += acc * 1000L; // bare number = seconds
        return total;
    }
}
