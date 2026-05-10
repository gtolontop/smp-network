package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.dict.PlayerDict;
import fr.smp.logger.util.RowFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RelationsCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String GUI_TITLE = "Hidden relations";
    private static final int PAGE_SIZE = 45;
    private final SMPLogger plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    public RelationsCommand(SMPLogger plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "relations_action");
        this.valueKey = new NamespacedKey(plugin, "relations_value");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "top" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "top" -> top(sender, parseLimit(args, 1, 10));
            case "player" -> {
                if (args.length < 2) usage(sender);
                else player(sender, args[1]);
            }
            case "pair" -> {
                if (args.length < 3) usage(sender);
                else pair(sender, args[1], args[2]);
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the GUI.", NamedTextColor.RED));
                    return true;
                }
                openGui(player, args.length >= 2 ? args[1] : null);
            }
            case "scanhomes", "homes" -> {
                double distance = args.length >= 2 ? parseDouble(args[1], plugin.getConfig().getDouble("relationships.home-scan.distance-blocks", 100.0)) : plugin.getConfig().getDouble("relationships.home-scan.distance-blocks", 100.0);
                scanHomes(sender, distance);
            }
            case "pause", "disable", "off" -> pause(sender, args);
            case "resume", "enable", "on" -> resume(sender);
            case "reset" -> {
                if (args.length < 3) usage(sender);
                else reset(sender, args[1], args[2]);
            }
            case "note" -> {
                if (args.length < 4) usage(sender);
                else note(sender, args[1], args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            }
            case "status" -> status(sender);
            default -> usage(sender);
        }
        return true;
    }

    private void top(CommandSender sender, int limit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.db().reader();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT player_low, player_high, score, level, evidence_count, last_signal, last_seen "
                                 + "FROM relation_pairs WHERE score > 0 ORDER BY score DESC, last_seen DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    sender.sendMessage(Component.text("--- hidden relation top ---", NamedTextColor.GOLD));
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        sender.sendMessage(Component.text(formatPairLine(rs), colorFor(rs.getString("level"))));
                    }
                    if (n == 0) sender.sendMessage(Component.text("No suspicious relation yet.", NamedTextColor.GRAY));
                }
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void player(CommandSender sender, String name) {
        PlayerDict.Entry entry = plugin.players().byName(name);
        if (entry == null) {
            sender.sendMessage(Component.text("Unknown player.", NamedTextColor.RED));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.db().reader();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT player_low, player_high, score, level, evidence_count, last_signal, last_seen "
                                 + "FROM relation_pairs WHERE player_low=? OR player_high=? ORDER BY score DESC, last_seen DESC LIMIT 20")) {
                ps.setInt(1, entry.id());
                ps.setInt(2, entry.id());
                try (ResultSet rs = ps.executeQuery()) {
                    sender.sendMessage(Component.text("--- relations for " + entry.name() + " ---", NamedTextColor.GOLD));
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        sender.sendMessage(Component.text(formatPairLine(rs), colorFor(rs.getString("level"))));
                    }
                    if (n == 0) sender.sendMessage(Component.text("No relation found.", NamedTextColor.GRAY));
                }
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void pair(CommandSender sender, String aName, String bName) {
        PlayerDict.Entry a = plugin.players().byName(aName);
        PlayerDict.Entry b = plugin.players().byName(bName);
        if (a == null || b == null) {
            sender.sendMessage(Component.text("Unknown player.", NamedTextColor.RED));
            return;
        }
        String pairKey = pairKey(a.id(), b.id());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.db().reader()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT * FROM relation_pairs WHERE pair_key=?")) {
                    ps.setString(1, pairKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            sender.sendMessage(Component.text("No relation data for this pair.", NamedTextColor.GRAY));
                            return;
                        }
                        sender.sendMessage(Component.text("--- " + name(rs.getInt("player_low")) + " <> " + name(rs.getInt("player_high")) + " ---", NamedTextColor.GOLD));
                        sender.sendMessage(Component.text("Score " + rs.getInt("score") + " | " + rs.getString("level")
                                + " | evidence " + rs.getInt("evidence_count"), colorFor(rs.getString("level"))));
                        sender.sendMessage(Component.text("Proximity " + rs.getInt("proximity_seconds") + "s"
                                + " | containers " + rs.getInt("shared_container_count")
                                + " | furnaces " + rs.getInt("shared_furnace_count")
                                + " | handoffs " + rs.getInt("chest_handoff_count"), NamedTextColor.AQUA));
                        sender.sendMessage(Component.text("Trades " + rs.getInt("trade_count")
                                + " | combat assists " + rs.getInt("combat_assist_count")
                                + " | homes " + rs.getInt("home_near_count")
                                + " | PvP contacts " + rs.getInt("pvp_contact_count"), NamedTextColor.AQUA));
                        String note = rs.getString("note");
                        if (note != null && !note.isBlank()) sender.sendMessage(Component.text("Note: " + note, NamedTextColor.YELLOW));
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT t, signal, points, world_id, x, y, z, detail FROM relation_evidence WHERE pair_key=? ORDER BY t DESC LIMIT 12")) {
                    ps.setString(1, pairKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String world = rs.getObject("world_id") == null ? "?" : plugin.worlds().nameOf(rs.getInt("world_id"));
                            sender.sendMessage(Component.text(RowFormatter.formatAgo((System.currentTimeMillis() - rs.getLong("t")) / 1000L)
                                    + " " + rs.getString("signal") + " " + signed(rs.getInt("points"))
                                    + " @ " + world + " " + rs.getInt("x") + "," + rs.getInt("y") + "," + rs.getInt("z")
                                    + " | " + rs.getString("detail"), NamedTextColor.GRAY));
                        }
                    }
                }
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void openGui(Player player, String filterName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<GuiRow> rows = new ArrayList<>();
            String sql = "SELECT player_low, player_high, score, level, evidence_count, last_signal, last_seen, "
                    + "shared_container_count, shared_furnace_count, chest_handoff_count, trade_count, combat_assist_count, "
                    + "home_near_count, pvp_contact_count, proximity_seconds, last_detail "
                    + "FROM relation_pairs WHERE score > 0 ";
            Integer filterId = null;
            if (filterName != null) {
                PlayerDict.Entry entry = plugin.players().byName(filterName);
                if (entry == null) {
                    player.sendMessage(Component.text("Unknown player.", NamedTextColor.RED));
                    return;
                }
                filterId = entry.id();
                sql += "AND (player_low=? OR player_high=?) ";
            }
            sql += "ORDER BY score DESC, last_seen DESC LIMIT " + PAGE_SIZE;
            try (Connection c = plugin.db().reader();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                if (filterId != null) {
                    ps.setInt(1, filterId);
                    ps.setInt(2, filterId);
                }
                GuiStats stats = loadGuiStats(c);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(new GuiRow(
                            rs.getInt("player_low"),
                            rs.getInt("player_high"),
                            rs.getInt("score"),
                            rs.getString("level"),
                            rs.getInt("evidence_count"),
                            rs.getString("last_signal"),
                            rs.getLong("last_seen"),
                            rs.getInt("shared_container_count"),
                            rs.getInt("shared_furnace_count"),
                            rs.getInt("chest_handoff_count"),
                            rs.getInt("trade_count"),
                            rs.getInt("combat_assist_count"),
                            rs.getInt("home_near_count"),
                            rs.getInt("pvp_contact_count"),
                            rs.getInt("proximity_seconds"),
                            rs.getString("last_detail")
                    ));
                }
                String filterValue = filterName == null ? "" : filterName;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory inv = Bukkit.createInventory(player, 54, Component.text(GUI_TITLE));
                    renderHeader(inv, stats, filterValue);
                    for (int i = 0; i < rows.size(); i++) inv.setItem(i + 9, itemFor(rows.get(i)));
                    player.openInventory(inv);
                });
            } catch (SQLException e) {
                player.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        String value = pdc.get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        String filter = value == null || value.isBlank() ? null : value;
        switch (action) {
            case "pair" -> player.performCommand("relations pair " + value);
            case "refresh" -> openGui(player, filter);
            case "top" -> player.performCommand("relations top 15");
            case "status" -> status(player);
            case "scanhomes" -> player.performCommand("relations scanhomes");
            case "pause15" -> {
                plugin.relationships().pauseFor(15L * 60L * 1000L, "GUI quick pause");
                player.sendMessage(Component.text("Relationship evidence checker paused for 15m.", NamedTextColor.YELLOW));
                openGui(player, filter);
            }
            case "pause60" -> {
                plugin.relationships().pauseFor(60L * 60L * 1000L, "GUI long pause");
                player.sendMessage(Component.text("Relationship evidence checker paused for 1h.", NamedTextColor.YELLOW));
                openGui(player, filter);
            }
            case "resume" -> {
                plugin.relationships().resume();
                player.sendMessage(Component.text("Relationship evidence checker runtime pause cleared.", NamedTextColor.GREEN));
                openGui(player, filter);
            }
            default -> {
            }
        }
    }

    private ItemStack itemFor(GuiRow row) {
        ItemStack item = new ItemStack(materialFor(row.level));
        ItemMeta meta = item.getItemMeta();
        String a = name(row.low);
        String b = name(row.high);
        meta.displayName(Component.text(a + " <> " + b + " [" + row.score + "]", colorFor(row.level)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Level: " + row.level + " | Evidence: " + row.evidence, NamedTextColor.GRAY));
        lore.add(Component.text("Last: " + (row.lastSignal == null ? "?" : row.lastSignal)
                + " | " + RowFormatter.formatAgo((System.currentTimeMillis() - row.lastSeen) / 1000L), NamedTextColor.GRAY));
        lore.add(Component.text("Proximity: " + row.proximitySeconds + "s | PvP contacts: " + row.pvpContacts, NamedTextColor.DARK_AQUA));
        lore.add(Component.text("Containers: " + row.containers + " | Furnaces: " + row.furnaces + " | Handoffs: " + row.handoffs, NamedTextColor.AQUA));
        lore.add(Component.text("Trades: " + row.trades + " | Combat: " + row.combat + " | Homes: " + row.homes, NamedTextColor.AQUA));
        if (row.lastDetail != null && !row.lastDetail.isBlank()) {
            lore.add(Component.text("Detail: " + truncateText(row.lastDetail, 44), NamedTextColor.GRAY));
        }
        lore.add(Component.text("Click: open full evidence", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "pair");
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, a + " " + b);
        item.setItemMeta(meta);
        return item;
    }

    private void renderHeader(Inventory inv, GuiStats stats, String filterName) {
        String filter = filterName == null ? "" : filterName;
        boolean active = plugin.relationships().isRecordingEnabled();
        Material statusMaterial = active ? Material.LIME_CONCRETE
                : plugin.relationships().isLobbyBlocked() ? Material.BARRIER : Material.ORANGE_CONCRETE;
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("Server: " + plugin.relationships().serverIdentity(), NamedTextColor.GRAY));
        statusLore.add(Component.text("Config: " + onOff(plugin.relationships().isConfiguredEnabled())
                + " | Sampler: " + onOff(plugin.relationships().proximitySamplerRunning()), NamedTextColor.GRAY));
        statusLore.add(Component.text("Lobby guard: " + onOff(plugin.relationships().isLobbyBlocked()), NamedTextColor.GRAY));
        if (plugin.relationships().isRuntimePaused()) {
            long until = plugin.relationships().pausedUntilMs();
            String remaining = until == Long.MAX_VALUE ? "manual resume" : formatDuration(Math.max(0L, until - System.currentTimeMillis()));
            statusLore.add(Component.text("Paused: " + remaining + " | " + plugin.relationships().pauseReason(), NamedTextColor.YELLOW));
        } else {
            statusLore.add(Component.text("Paused: OFF", NamedTextColor.GREEN));
        }
        inv.setItem(0, actionItem(statusMaterial, "Evidence checker: " + (active ? "ON" : "OFF"),
                active ? NamedTextColor.GREEN : NamedTextColor.RED, statusLore, "status", filter));

        inv.setItem(1, actionItem(Material.WRITABLE_BOOK, "Evidence map", NamedTextColor.AQUA, List.of(
                Component.text("Pairs: " + stats.pairs + " | Evidence: " + stats.evidence, NamedTextColor.GRAY),
                Component.text("Watch+: " + stats.watch + " | Suspect+: " + stats.suspect, NamedTextColor.YELLOW),
                Component.text("Probable+: " + stats.probable + " | Confirmed: " + stats.confirmed, NamedTextColor.GOLD),
                Component.text("Click: show top 15 in chat", NamedTextColor.DARK_GRAY)
        ), "top", filter));

        inv.setItem(2, actionItem(Material.SPYGLASS, "Filtered view", NamedTextColor.LIGHT_PURPLE, List.of(
                Component.text(filter.isBlank() ? "Showing: global top pairs" : "Showing: " + filter, NamedTextColor.GRAY),
                Component.text("Use /relations gui <player> to focus a player", NamedTextColor.DARK_GRAY)
        ), "refresh", filter));

        inv.setItem(3, actionItem(Material.CLOCK, "Pause 15m", NamedTextColor.YELLOW, List.of(
                Component.text("Stops all new relationship evidence.", NamedTextColor.GRAY),
                Component.text("Auto-resumes after 15 minutes.", NamedTextColor.GRAY)
        ), "pause15", filter));

        inv.setItem(4, actionItem(Material.REDSTONE_TORCH, "Pause 1h", NamedTextColor.GOLD, List.of(
                Component.text("Good for events, lobby tests, or public gatherings.", NamedTextColor.GRAY),
                Component.text("Auto-resumes after 1 hour.", NamedTextColor.GRAY)
        ), "pause60", filter));

        inv.setItem(5, actionItem(Material.EMERALD_BLOCK, "Resume now", NamedTextColor.GREEN, List.of(
                Component.text("Clears the runtime pause.", NamedTextColor.GRAY),
                Component.text("Config and lobby guard still apply.", NamedTextColor.DARK_GRAY)
        ), "resume", filter));

        inv.setItem(6, actionItem(Material.COMPASS, "Scan homes", NamedTextColor.AQUA, List.of(
                Component.text("Runs the configured close-home scan.", NamedTextColor.GRAY),
                Component.text("Lobby/hub worlds stay ignored.", NamedTextColor.DARK_GRAY)
        ), "scanhomes", filter));

        inv.setItem(7, actionItem(Material.AMETHYST_SHARD, "Refresh", NamedTextColor.LIGHT_PURPLE, List.of(
                Component.text("Reloads scores, status and counters.", NamedTextColor.GRAY)
        ), "refresh", filter));

        inv.setItem(8, actionItem(Material.NAME_TAG, "Controls", NamedTextColor.WHITE, List.of(
                Component.text("/relations pause <10m|1h|1d> [reason]", NamedTextColor.GRAY),
                Component.text("/relations resume", NamedTextColor.GRAY),
                Component.text("/relations status", NamedTextColor.GRAY)
        ), "status", filter));
    }

    private ItemStack actionItem(Material material, String name, NamedTextColor color, List<Component> lore,
                                 String action, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (value != null) meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private GuiStats loadGuiStats(Connection c) throws SQLException {
        try (StatementPair stats = new StatementPair(c)) {
            int watchThreshold = plugin.getConfig().getInt("relationships.levels.watch", 18);
            int suspectThreshold = plugin.getConfig().getInt("relationships.levels.suspect", 35);
            int probableThreshold = plugin.getConfig().getInt("relationships.levels.probable", 55);
            int confirmedThreshold = plugin.getConfig().getInt("relationships.levels.confirmed", 80);
            return new GuiStats(
                    stats.scalar("SELECT COUNT(*) FROM relation_pairs"),
                    stats.scalar("SELECT COUNT(*) FROM relation_evidence"),
                    stats.scalar("SELECT COUNT(*) FROM relation_pairs WHERE score >= " + watchThreshold),
                    stats.scalar("SELECT COUNT(*) FROM relation_pairs WHERE score >= " + suspectThreshold),
                    stats.scalar("SELECT COUNT(*) FROM relation_pairs WHERE score >= " + probableThreshold),
                    stats.scalar("SELECT COUNT(*) FROM relation_pairs WHERE score >= " + confirmedThreshold)
            );
        }
    }

    private void scanHomes(CommandSender sender, double distance) {
        int matches = plugin.relationships().scanHomes(distance, sender);
        if (matches < 0) return;
        sender.sendMessage(Component.text("Home scan done: " + matches + " close-home matches within " + Math.round(distance) + " blocks.", NamedTextColor.GREEN));
    }

    private void pause(CommandSender sender, String[] args) {
        long durationMs = args.length >= 2 ? parseDuration(args[1]) : 0L;
        if (durationMs < 0L) {
            sender.sendMessage(Component.text("Invalid duration. Examples: 10m, 1h, 1h30m, 1d.", NamedTextColor.RED));
            return;
        }
        String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "command";
        plugin.relationships().pauseFor(durationMs, reason);
        String duration = durationMs == 0L ? "until manual resume" : "for " + formatDuration(durationMs);
        sender.sendMessage(Component.text("Relationship evidence checker paused " + duration + ".", NamedTextColor.YELLOW));
    }

    private void resume(CommandSender sender) {
        plugin.relationships().resume();
        if (plugin.relationships().isLobbyBlocked()) {
            sender.sendMessage(Component.text("Runtime pause cleared, but lobby guard still blocks relationship evidence.", NamedTextColor.YELLOW));
        } else if (!plugin.relationships().isConfiguredEnabled()) {
            sender.sendMessage(Component.text("Runtime pause cleared, but relationships.enabled is false in config.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Relationship evidence checker resumed.", NamedTextColor.GREEN));
        }
    }

    private void reset(CommandSender sender, String aName, String bName) {
        PlayerDict.Entry a = plugin.players().byName(aName);
        PlayerDict.Entry b = plugin.players().byName(bName);
        if (a == null || b == null) {
            sender.sendMessage(Component.text("Unknown player.", NamedTextColor.RED));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.relationships().resetPair(a, b);
                sender.sendMessage(Component.text("Relation reset.", NamedTextColor.GREEN));
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void note(CommandSender sender, String aName, String bName, String note) {
        PlayerDict.Entry a = plugin.players().byName(aName);
        PlayerDict.Entry b = plugin.players().byName(bName);
        if (a == null || b == null) {
            sender.sendMessage(Component.text("Unknown player.", NamedTextColor.RED));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.relationships().setNote(a, b, note);
                sender.sendMessage(Component.text("Relation note saved.", NamedTextColor.GREEN));
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void status(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.db().reader();
                 StatementPair stats = new StatementPair(c)) {
                sender.sendMessage(Component.text("--- relationship status ---", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Recording: " + onOff(plugin.relationships().isRecordingEnabled())
                        + " | Server: " + plugin.relationships().serverIdentity()
                        + " | Lobby guard: " + onOff(plugin.relationships().isLobbyBlocked()), NamedTextColor.AQUA));
                if (plugin.relationships().isRuntimePaused()) {
                    long until = plugin.relationships().pausedUntilMs();
                    String remaining = until == Long.MAX_VALUE ? "manual resume" : formatDuration(Math.max(0L, until - System.currentTimeMillis()));
                    sender.sendMessage(Component.text("Pause: " + remaining + " | reason: " + plugin.relationships().pauseReason(), NamedTextColor.YELLOW));
                }
                sender.sendMessage(Component.text("Pairs: " + stats.scalar("SELECT COUNT(*) FROM relation_pairs")
                        + " | Evidence: " + stats.scalar("SELECT COUNT(*) FROM relation_evidence"), NamedTextColor.AQUA));
                sender.sendMessage(Component.text("Suspect+: " + stats.scalar("SELECT COUNT(*) FROM relation_pairs WHERE score >= "
                        + plugin.getConfig().getInt("relationships.levels.suspect", 35)), NamedTextColor.YELLOW));
            } catch (SQLException e) {
                sender.sendMessage(Component.text("DB error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("/relations top [limit]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/relations player <player>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/relations pair <a> <b>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/relations gui [player]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/relations scanhomes [distance]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/relations pause <duration> [reason] | resume", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/relations note <a> <b> <text> | reset <a> <b> | status", NamedTextColor.GRAY));
    }

    private String formatPairLine(ResultSet rs) throws SQLException {
        return name(rs.getInt("player_low")) + " <> " + name(rs.getInt("player_high"))
                + " | " + rs.getString("level")
                + " score=" + rs.getInt("score")
                + " evidence=" + rs.getInt("evidence_count")
                + " last=" + (rs.getString("last_signal") == null ? "?" : rs.getString("last_signal"))
                + " " + RowFormatter.formatAgo((System.currentTimeMillis() - rs.getLong("last_seen")) / 1000L);
    }

    private String name(int id) {
        PlayerDict.Entry entry = plugin.players().byId(id);
        return entry == null ? "#" + id : entry.name();
    }

    private static NamedTextColor colorFor(String level) {
        if (level == null) return NamedTextColor.GRAY;
        return switch (level) {
            case "CONFIRMED" -> NamedTextColor.RED;
            case "PROBABLE" -> NamedTextColor.GOLD;
            case "SUSPECT" -> NamedTextColor.YELLOW;
            case "WATCH" -> NamedTextColor.AQUA;
            default -> NamedTextColor.GRAY;
        };
    }

    private static Material materialFor(String level) {
        if (level == null) return Material.PAPER;
        return switch (level) {
            case "CONFIRMED" -> Material.REDSTONE_BLOCK;
            case "PROBABLE" -> Material.ORANGE_CONCRETE;
            case "SUSPECT" -> Material.YELLOW_CONCRETE;
            case "WATCH" -> Material.LIGHT_BLUE_CONCRETE;
            default -> Material.PAPER;
        };
    }

    private static String pairKey(int a, int b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    private static int parseLimit(String[] args, int index, int fallback) {
        if (args.length <= index) return fallback;
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(args[index])));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseDuration(String value) {
        if (value == null || value.isBlank()) return -1L;
        String input = value.trim().toLowerCase(Locale.ROOT);
        long total = 0L;
        int i = 0;
        while (i < input.length()) {
            int start = i;
            while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
            if (start == i) return -1L;
            long amount;
            try {
                amount = Long.parseLong(input.substring(start, i));
            } catch (NumberFormatException ignored) {
                return -1L;
            }
            char unit = i < input.length() && Character.isLetter(input.charAt(i)) ? input.charAt(i++) : 'm';
            long multiplier = switch (unit) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 60L * 60L * 1000L;
                case 'd', 'j' -> 24L * 60L * 60L * 1000L;
                default -> -1L;
            };
            if (multiplier < 0L) return -1L;
            if (amount > Long.MAX_VALUE / multiplier) return -1L;
            long add = amount * multiplier;
            if (Long.MAX_VALUE - total < add) return -1L;
            total += add;
        }
        return total <= 0L ? -1L : total;
    }

    private static String formatDuration(long durationMs) {
        long seconds = Math.max(1L, durationMs / 1000L);
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        List<String> parts = new ArrayList<>();
        if (days > 0L) parts.add(days + "d");
        if (hours > 0L) parts.add(hours + "h");
        if (minutes > 0L) parts.add(minutes + "m");
        if (parts.isEmpty() || seconds > 0L && parts.size() < 2) parts.add(seconds + "s");
        return String.join(" ", parts.subList(0, Math.min(2, parts.size())));
    }

    private static String signed(int points) {
        return points >= 0 ? "+" + points : String.valueOf(points);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String truncateText(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("top", "player", "pair", "gui", "scanhomes", "pause", "resume", "disable", "enable", "reset", "note", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private record GuiRow(int low, int high, int score, String level, int evidence, String lastSignal,
                          long lastSeen, int containers, int furnaces, int handoffs, int trades, int combat,
                          int homes, int pvpContacts, int proximitySeconds, String lastDetail) {}

    private record GuiStats(int pairs, int evidence, int watch, int suspect, int probable, int confirmed) {}

    private static final class StatementPair implements AutoCloseable {
        private final java.sql.Statement statement;

        private StatementPair(Connection connection) throws SQLException {
            this.statement = connection.createStatement();
        }

        private int scalar(String sql) throws SQLException {
            try (ResultSet rs = statement.executeQuery(sql)) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public void close() throws SQLException {
            statement.close();
        }
    }
}
