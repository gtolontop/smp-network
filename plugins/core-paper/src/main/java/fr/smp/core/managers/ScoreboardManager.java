package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.data.PlayerDataManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ScoreboardManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String OBJ_NAME = "smp_sb";

    private final SMPCore plugin;
    private final PlayerDataManager players;
    private final TeamManager teams;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** Last rendered serialized lines per player — used for diff to skip packet work. */
    private final Map<UUID, List<String>> lastLines = new HashMap<>();
    /** Last title string per player — to detect title changes without re-parsing. */
    private final Map<UUID, String> lastTitle = new HashMap<>();

    /** Unix seconds for next global keyall. */
    private final AtomicLong nextKeyall = new AtomicLong(0);

    // Config values cached at startup to avoid per-tick YAML lookup × online players.
    // Reloaded on /reload via refreshConfig().
    private volatile String cfgTitle;
    private volatile List<String> cfgLines;
    private volatile long cfgKeyallIntervalSec;

    public ScoreboardManager(SMPCore plugin, PlayerDataManager players, TeamManager teams) {
        this.plugin = plugin;
        this.players = players;
        this.teams = teams;
        refreshConfig();
        nextKeyall.set(System.currentTimeMillis() / 1000 + cfgKeyallIntervalSec);
    }

    public void refreshConfig() {
        this.cfgTitle = plugin.getConfig().getString("scoreboard.title",
                "<gradient:#67e8f9:#a78bfa><bold>SaphirSMP</bold></gradient>");
        List<String> configured = plugin.getConfig().getStringList("scoreboard.lines");
        this.cfgLines = configured.isEmpty() ? null : List.copyOf(configured);
        this.cfgKeyallIntervalSec = plugin.getConfig().getLong("keyall.interval-minutes", 60) * 60;
    }

    public void apply(Player p) {
        PlayerData d = players.get(p);
        if (d == null || !d.scoreboardEnabled()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());
        p.setScoreboard(board);
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        lastLines.remove(p.getUniqueId());
        lastTitle.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() { tickAll(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void tickKeyallReset() {
        nextKeyall.set(System.currentTimeMillis() / 1000 + cfgKeyallIntervalSec);
    }

    public long keyallLeft() {
        return Math.max(0, nextKeyall.get() - System.currentTimeMillis() / 1000);
    }

    private void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = players.get(p);
            if (d == null) continue;
            if (!d.scoreboardEnabled()) {
                if (boards.containsKey(p.getUniqueId())) remove(p);
                continue;
            }
            render(p, d);
        }
    }

    private void render(Player p, PlayerData d) {
        UUID id = p.getUniqueId();
        Scoreboard b = boards.computeIfAbsent(id,
                k -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (p.getScoreboard() != b) p.setScoreboard(b);

        Objective obj = b.getObjective(OBJ_NAME);
        String previousTitle = lastTitle.get(id);
        if (obj == null) {
            // First render for this player — create objective once and keep it alive.
            obj = b.registerNewObjective(OBJ_NAME, Criteria.DUMMY, MM.deserialize(cfgTitle));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            try { obj.numberFormat(NumberFormat.blank()); } catch (Throwable ignored) {}
            lastTitle.put(id, cfgTitle);
        } else if (!cfgTitle.equals(previousTitle)) {
            // Title changed (config reload). Update the displayName in place — do NOT
            // unregister/recreate, that sends a full clear+reinit packet.
            obj.displayName(MM.deserialize(cfgTitle));
            lastTitle.put(id, cfgTitle);
        }

        List<String> newLines = buildLines(d);
        List<String> oldLines = lastLines.get(id);
        if (oldLines != null && oldLines.equals(newLines)) {
            // Nothing changed — zero packets this tick. This is the common case by far
            // (money/kills/playtime only tick over every few seconds/minutes).
            return;
        }

        // At least one line changed. Reset the current entries and re-add. We only pay
        // this cost when there is actually a visible change — previously every tick did
        // this work for every player regardless.
        for (String entry : new ArrayList<>(b.getEntries())) {
            b.resetScores(entry);
        }
        int score = newLines.size();
        for (String entry : newLines) {
            obj.getScore(entry).setScore(score--);
        }
        lastLines.put(id, newLines);
    }

    private List<String> buildLines(PlayerData d) {
        String teamTag;
        if (d.teamId() != null) {
            TeamManager.Team t = teams.get(d.teamId());
            if (t != null) {
                teamTag = t.color() + "[" + t.tag() + "]<reset>";
            } else {
                teamTag = "<gray>No team</gray>";
            }
        } else {
            teamTag = "<gray>No team</gray>";
        }

        Player p = Bukkit.getPlayer(d.uuid());
        int ping = p != null ? p.getPing() : 0;
        String deathOpen = d.deaths() > 0 ? "<red>" : "<gray>";
        String deathClose = d.deaths() > 0 ? "</red>" : "</gray>";

        List<String> lines = cfgLines != null ? cfgLines : List.of(
                "<dark_gray>────────────</dark_gray>",
                "<green>$</green> <white>Money</white> <green>" + Msg.money(d.money()) + "</green>",
                "<aqua>◆</aqua> <white>Saphirs</white> <aqua>" + d.shards() + "</aqua>",
                "<red>⚔</red> <white>Kills</white> <red>" + d.kills() + "</red>",
                deathOpen + "☠" + deathClose + " <white>Deaths</white> " + deathOpen + d.deaths() + deathClose,
                "<yellow>⏱</yellow> <white>Playtime</white> <yellow>" + Msg.duration(d.totalPlaytimeWithSession()) + "</yellow>",
                "<blue>⚑</blue> <white>Team</white> " + teamTag,
                "<dark_gray>────────────</dark_gray>",
                "<gray>" + ping + "ms</gray>"
        );

        List<String> out = new ArrayList<>(lines.size());
        int uniqIdx = 0;
        for (String line : lines) {
            // Scoreboard entries must be unique; append zero-width suffix per index.
            String suffix = invisibleSuffix(uniqIdx++);
            Component comp = MM.deserialize(line);
            out.add(legacy(comp) + suffix);
        }
        return out;
    }

    private String invisibleSuffix(int idx) {
        char[] hex = "0123456789abcdef".toCharArray();
        return "§" + hex[idx & 0xF] + "§r";
    }

    private String legacy(Component c) {
        return LegacyComponentSerializer.legacySection().serialize(c);
    }
}
