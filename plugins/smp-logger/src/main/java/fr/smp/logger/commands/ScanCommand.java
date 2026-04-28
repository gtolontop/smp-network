package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.scan.RegionScanner;
import fr.smp.logger.scan.ScanModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ScanCommand implements CommandExecutor, TabCompleter {

    private final SMPLogger plugin;

    public ScanCommand(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean playersOnly = false;
        boolean worldOnly = false;
        String materialArg = null;

        for (String a : args) {
            String low = a.toLowerCase(Locale.ROOT);
            if (low.equals("players") || low.equals("player")) playersOnly = true;
            else if (low.equals("world") || low.equals("chests")) worldOnly = true;
            else if (!low.equals("summary") && !low.equals("all")) materialArg = a;
        }

        if (materialArg != null) {
            Material mat = Material.matchMaterial(materialArg);
            if (mat == null) {
                sender.sendMessage(Component.text("Matériau inconnu: " + materialArg, NamedTextColor.RED));
                return true;
            }
            showSingle(sender, mat, playersOnly, worldOnly);
        } else {
            showSummary(sender, playersOnly, worldOnly);
        }
        return true;
    }

    private void showSingle(CommandSender sender, Material mat, boolean playersOnly, boolean worldOnly) {
        Map<UUID, int[]> onlineData = playersOnly || !worldOnly ? plugin.scanModule().snapshotOnline(mat) : Map.of();
        boolean scanPlayers = !worldOnly;
        boolean scanWorld = !playersOnly;
        long[] lastProgressMs = {0L};

        sender.sendMessage(Component.text("Scan en cours" + (scanWorld ? " (joueurs + monde)" : "") + "...", NamedTextColor.GRAY));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ScanModule.SingleResult playerResult = scanPlayers
                    ? plugin.scanModule().scan(mat, onlineData)
                    : new ScanModule.SingleResult(mat, 0, 0, List.of());

            RegionScanner.SingleResult worldResult = scanWorld
                    ? plugin.scanModule().regionScanner().scanSingle(mat, (done, total, containers, items) ->
                        sendProgress(sender, done, total, containers, lastProgressMs))
                    : new RegionScanner.SingleResult(mat, 0, 0, 0, 0L);

            int grandTotal = playerResult.total() + worldResult.total();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text("─── scan: " + mat.name() + " ───", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Total: ", NamedTextColor.WHITE)
                        .append(Component.text(grandTotal, NamedTextColor.GREEN)));
                if (scanPlayers) {
                    sender.sendMessage(Component.text("  Joueurs: ", NamedTextColor.GRAY)
                            .append(Component.text(playerResult.total(), NamedTextColor.GREEN))
                            .append(Component.text(" (chez " + playerResult.playerCount() + ")", NamedTextColor.DARK_GRAY)));
                }
                if (scanWorld) {
                    sender.sendMessage(Component.text("  Monde: ", NamedTextColor.GRAY)
                            .append(Component.text(worldResult.total(), NamedTextColor.GREEN))
                            .append(Component.text(" (" + worldResult.containersFound() + " conteneurs, "
                                    + worldResult.regionsScanned() + " régions, "
                                    + String.format("%.1f", worldResult.elapsedMs() / 1000.0) + "s)", NamedTextColor.DARK_GRAY)));
                }

                if (scanPlayers && !playerResult.top().isEmpty()) {
                    sender.sendMessage(Component.text(""));
                    int shown = 0;
                    for (ScanModule.PlayerBreakdown pb : playerResult.top()) {
                        if (shown++ >= 10) break;
                        String status = pb.online() ? "●" : "○";
                        NamedTextColor statusColor = pb.online() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
                        sender.sendMessage(Component.text("  " + status + " ", statusColor)
                                .append(Component.text(pb.name(), NamedTextColor.YELLOW))
                                .append(Component.text(" — " + pb.total(), NamedTextColor.GREEN))
                                .append(Component.text(" (inv: " + pb.inv() + ", ender: " + pb.ender() + ")", NamedTextColor.GRAY)));
                    }
                    sender.sendMessage(Component.text("  ● en ligne  ○ hors ligne", NamedTextColor.DARK_GRAY));
                }
            });
        });
    }

    private void showSummary(CommandSender sender, boolean playersOnly, boolean worldOnly) {
        Set<Material> targets = plugin.scanModule().parseRareItems();
        boolean scanPlayers = !worldOnly;
        boolean scanWorld = !playersOnly;

        Map<Material, Integer> onlineTotals = scanPlayers ? plugin.scanModule().snapshotOnlineSummary(targets) : Map.of();
        Set<UUID> onlineUuids = new HashSet<>();
        if (scanPlayers) for (Player p : plugin.getServer().getOnlinePlayers()) onlineUuids.add(p.getUniqueId());
        long[] lastProgressMs = {0L};

        sender.sendMessage(Component.text("Scan en cours" + (scanWorld ? " (joueurs + monde)" : "") + "...", NamedTextColor.GRAY));
        long t0 = System.currentTimeMillis();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Material, Integer> playerTotals = scanPlayers
                    ? plugin.scanModule().scanSummary(targets, onlineTotals, onlineUuids)
                    : zeros(targets);
            int playersScanned = scanPlayers ? plugin.scanModule().countTotalPlayers() : 0;

            RegionScanner.SummaryResult worldResult = scanWorld
                    ? plugin.scanModule().regionScanner().scanSummary(targets, (done, total, containers, items) ->
                        sendProgress(sender, done, total, containers, lastProgressMs))
                    : new RegionScanner.SummaryResult(zeros(targets), 0, 0, 0L);

            long elapsed = System.currentTimeMillis() - t0;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text("─── circulation summary ───", NamedTextColor.GOLD));
                if (scanPlayers) {
                    sender.sendMessage(Component.text(playersScanned + " joueurs scannés",
                            NamedTextColor.GRAY));
                }
                if (scanWorld) {
                    sender.sendMessage(Component.text(worldResult.regionsScanned() + " régions scannées ("
                            + worldResult.containersFound() + " conteneurs)", NamedTextColor.GRAY));
                }
                sender.sendMessage(Component.text("Temps total: " + String.format("%.1f", elapsed / 1000.0) + "s",
                        NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text(""));

                Map<Material, int[]> merged = new LinkedHashMap<>();
                for (Material m : targets) merged.put(m, new int[]{0, 0});
                for (var e : playerTotals.entrySet()) merged.computeIfAbsent(e.getKey(), k -> new int[]{0, 0})[0] = e.getValue();
                for (var e : worldResult.totals().entrySet()) merged.computeIfAbsent(e.getKey(), k -> new int[]{0, 0})[1] = e.getValue();

                List<Map.Entry<Material, int[]>> sorted = new ArrayList<>(merged.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));
                for (var e : sorted) {
                    int playerCount = e.getValue()[0];
                    int worldCount = e.getValue()[1];
                    int total = playerCount + worldCount;
                    if (total == 0) continue;
                    String name = padRight(e.getKey().name(), 28);
                    Component line = Component.text("  " + name + " ", NamedTextColor.GRAY)
                            .append(Component.text(String.valueOf(total), NamedTextColor.GREEN));
                    if (scanPlayers && scanWorld) {
                        line = line.append(Component.text(" (j:" + playerCount + " m:" + worldCount + ")",
                                NamedTextColor.DARK_GRAY));
                    }
                    sender.sendMessage(line);
                }
            });
        });
    }

    private static Map<Material, Integer> zeros(Set<Material> targets) {
        Map<Material, Integer> z = new HashMap<>();
        for (Material m : targets) z.put(m, 0);
        return z;
    }

    private void sendProgress(CommandSender sender, int done, int total, int containers, long[] lastMs) {
        if (total <= 0) return;
        long now = System.currentTimeMillis();
        if (done < total && now - lastMs[0] < 5000L) return;
        lastMs[0] = now;
        int pct = (int) (100L * done / total);
        plugin.getServer().getScheduler().runTask(plugin, () ->
            sender.sendMessage(Component.text("  ... " + pct + "% (" + done + "/" + total + " régions, "
                    + containers + " conteneurs)", NamedTextColor.DARK_GRAY)));
    }

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append('.');
        return sb.toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String s : new String[]{"summary", "players", "world"}) {
            if (s.startsWith(prefix)) suggestions.add(s);
        }
        for (Material m : plugin.scanModule().parseRareItems()) {
            String name = m.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) suggestions.add(name);
        }
        return suggestions;
    }
}
