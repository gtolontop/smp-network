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
    /** Cheap signature of player data — short-circuits buildLines() when nothing changed.
     *  Saves the MiniMessage parse + legacy serialization × 9 lines × N players × 0.5/s. */
    private final Map<UUID, Long> lastSig = new HashMap<>();

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
        lastSig.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() { tickAll(); }
        }.runTaskTimer(plugin, 25L, 40L);
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

        // Cheap signature short-circuit: hash only the values that actually drive the
        // displayed lines. When unchanged we skip buildLines() entirely (which otherwise
        // does 9× MiniMessage.deserialize + 9× legacy serialization per player per cycle).
        long sig = signature(d, p.getPing());
        Long old = lastSig.get(id);
        if (old != null && old == sig) {
            return;
        }

        List<String> newLines = buildLines(d);
        lastSig.put(id, sig);
        List<String> oldLines = lastLines.get(id);
        if (oldLines != null && oldLines.equals(newLines)) {
            // Signature changed but rendered output is identical (rare — e.g. hash
            // collision or value rounded out of display granularity). Skip packet work.
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

    /**
     * Cheap rolling hash over the values that drive the rendered scoreboard.
     * Quantized so visually-identical fluctuations (e.g. ping jitter ±2ms) don't
     * trigger a full rebuild. Playtime is bucketed at the granularity actually
     * displayed by Msg.duration() so seconds drop out for >1h players.
     */
    private long signature(PlayerData d, int ping) {
        long h = 1469598103934665603L; // FNV-1a offset basis
        h = mix(h, Double.doubleToLongBits(d.money()));
        h = mix(h, d.shards());
        h = mix(h, d.kills());
        h = mix(h, d.deaths());
        h = mix(h, playtimeBucket(d.totalPlaytimeWithSession()));
        h = mix(h, ping / 5);
        h = mix(h, d.teamId() != null ? d.teamId().hashCode() : 0);
        if (d.teamId() != null) {
            TeamManager.Team t = teams.get(d.teamId());
            if (t != null) h = mix(h, ((long) t.tag().hashCode() << 32) ^ t.color().hashCode());
        }
        h = mix(h, cfgTitle.hashCode());
        h = mix(h, cfgLines != null ? cfgLines.hashCode() : 0);
        return h;
    }

    private static long mix(long h, long v) {
        h ^= v;
        h *= 1099511628211L; // FNV-1a prime
        return h;
    }

    /**
     * Bucket playtime to the granularity Msg.duration() actually displays:
     *  &lt; 60s → per second, &lt; 60m → per second, &lt; 24h → per minute, ≥ 1d → per hour.
     */
    private static long playtimeBucket(long sec) {
        if (sec < 3600) return sec;          // shows seconds
        if (sec < 86400) return sec / 60;    // shows "Hh Mm"
        return sec / 3600;                   // shows "Dd Hh"
    }

    private String legacy(Component c) {
        return LegacyComponentSerializer.legacySection().serialize(c);
    }
}
