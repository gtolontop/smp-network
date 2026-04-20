package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.storage.Database;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks the "chassé" of the day — the player with the most daily kills
 * (above a configured threshold). Applies glow, [CHASSÉ] tag, blood-trail
 * particles, and places a one-per-day automatic bounty on them.
 */
public class HuntedManager {

    public static final UUID SERVER_UUID = new UUID(0L, 0L);
    public static final String SERVER_NAME = "SERVEUR";
    public static final String TEAM_NAME = "smp_hunted";

    private final SMPCore plugin;
    private final Database db;

    private UUID currentHunted;
    private String currentHuntedName;
    private int currentHuntedKills;
    private LocalDate currentDate;
    /** Date (yyyy-MM-dd) on which the auto-bounty was last placed. null/older = can place today. */
    private String lastBountyDate;

    private BukkitTask particleTask;
    private BukkitTask dayTask;

    public HuntedManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        this.currentDate = LocalDate.now();
    }

    public void start() {
        ensureHuntedTeam();
        loadState();
        // Re-apply effects to the persisted hunted if they're already online (post-restart).
        Player online = onlineHunted();
        if (online != null) {
            applyVisualEffects(online);
            ensureParticleTask();
            refreshDisplayEntries(online);
        }
        // Minute-resolution rollover check.
        dayTask = new BukkitRunnable() {
            @Override public void run() { rotateDayIfNeeded(); }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30);
    }

    public void shutdown() {
        if (dayTask != null) dayTask.cancel();
        if (particleTask != null) particleTask.cancel();
        Player p = onlineHunted();
        if (p != null) stripVisualEffects(p);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("hunted.enabled", true);
    }

    public int minKills() {
        return plugin.getConfig().getInt("hunted.min-kills", 5);
    }

    public double bountyAmount() {
        return plugin.getConfig().getDouble("hunted.bounty-amount", 100_000d);
    }

    public UUID currentHunted() { return currentHunted; }
    public String currentHuntedName() { return currentHuntedName; }

    public boolean isHunted(UUID uuid) {
        return currentHunted != null && currentHunted.equals(uuid);
    }

    /**
     * Called from DeathListener after the killer's dailyKills has been
     * incremented. If the killer now has more daily kills than the current
     * hunted (and passes the threshold), they take the title.
     */
    public void onKill(Player killer, PlayerData killerData) {
        if (!isEnabled() || killer == null || killerData == null) return;
        rotateDayIfNeeded();

        int kills = killerData.dailyKills();
        if (kills < minKills()) return;

        if (currentHunted == null) {
            promote(killer, killerData, "<red>atteint " + kills + " kills aujourd'hui</red>");
            return;
        }
        if (currentHunted.equals(killer.getUniqueId())) {
            currentHuntedKills = kills;
            saveState();
            return;
        }
        if (kills > currentHuntedKills) {
            promote(killer, killerData, "<red>a dépassé le chassé précédent (" + kills + " kills)</red>");
        }
    }

    /** Called when the hunted dies. Does not clear the status — a new top killer will replace them naturally. */
    public void onHuntedDeath(Player victim) {
        if (!isHunted(victim.getUniqueId())) return;
        // The bounty removal is handled by DeathListener.payoutBounty if a killer exists.
        // We simply leave the hunted status in place; the next kill recomputes it.
    }

    public void refreshOnJoin(Player p) {
        if (!isEnabled()) return;
        rotateDayIfNeeded();
        if (isHunted(p.getUniqueId())) {
            applyVisualEffects(p);
            ensureParticleTask();
        }
    }

    // ------------------------------------------------------------------

    private void promote(Player newHunted, PlayerData data, String reasonMm) {
        Player old = onlineHunted();
        if (old != null && !old.getUniqueId().equals(newHunted.getUniqueId())) {
            stripVisualEffects(old);
        }

        currentHunted = newHunted.getUniqueId();
        currentHuntedName = newHunted.getName();
        currentHuntedKills = data.dailyKills();
        saveState();

        applyVisualEffects(newHunted);
        ensureParticleTask();

        // Broadcast.
        Bukkit.broadcast(Msg.mm("<dark_red>☠ <red><white>" + newHunted.getName() +
                "</white> est désormais <bold>CHASSÉ</bold> — " + reasonMm + "</red>"));

        // Refresh nametag/tab/chat prefixes so the tag appears immediately.
        refreshDisplayEntries(newHunted);

        // Auto-bounty (once per day, even if the hunted changes person).
        String today = currentDate.toString();
        if (!today.equals(lastBountyDate) && plugin.bounties() != null) {
            double amount = bountyAmount();
            if (amount > 0) {
                double total = plugin.bounties().add(newHunted.getUniqueId(), newHunted.getName(),
                        SERVER_UUID, SERVER_NAME, amount);
                if (total > 0) {
                    lastBountyDate = today;
                    saveState();
                    Bukkit.broadcast(Msg.mm("<dark_red>☠ <gold>Une prime de $" + Msg.money(amount) +
                            "</gold> <red>a été placée automatiquement sur <white>" + newHunted.getName() +
                            "</white> !</red>"));
                }
            }
        }

        plugin.logs().log(LogCategory.COMBAT,
                "hunted.set name=" + newHunted.getName() + " kills=" + currentHuntedKills);
    }

    private void applyVisualEffects(Player p) {
        p.setGlowing(true);
        putInHuntedTeam(p);
    }

    private void stripVisualEffects(Player p) {
        p.setGlowing(false);
        removeFromHuntedTeam(p);
    }

    private Player onlineHunted() {
        if (currentHunted == null) return null;
        return Bukkit.getPlayer(currentHunted);
    }

    private void ensureParticleTask() {
        if (particleTask != null && !particleTask.isCancelled()) return;
        particleTask = new HuntedParticleTask(this).runTaskTimer(plugin, 5L, 5L);
    }

    private void refreshDisplayEntries(Player p) {
        if (plugin.nametags() != null) plugin.nametags().refresh(p);
        if (plugin.tabList() != null) plugin.tabList().update(p);
    }

    private void rotateDayIfNeeded() {
        LocalDate today = LocalDate.now();
        if (today.equals(currentDate)) return;
        currentDate = today;

        // Per-player daily kill counts reset lazily in DeathListener.bumpDailyKills
        // (whenever dailyKillsDate != today). Do not wipe them here or we risk
        // clobbering a kill that just landed in the same tick as the rollover.

        Player old = onlineHunted();
        if (old != null) stripVisualEffects(old);
        UUID previous = currentHunted;
        currentHunted = null;
        currentHuntedName = null;
        currentHuntedKills = 0;

        saveState();
        if (previous != null) {
            Player prev = Bukkit.getPlayer(previous);
            if (prev != null) refreshDisplayEntries(prev);
        }
        Bukkit.broadcast(Msg.mm("<dark_red>☠ <red>Un nouveau jour commence — le chassé est réinitialisé.</red>"));
    }

    private void ensureHuntedTeam() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = main.getTeam(TEAM_NAME);
        if (t == null) t = main.registerNewTeam(TEAM_NAME);
        t.color(NamedTextColor.RED);
        Component prefix = MiniMessage.miniMessage().deserialize(
                "<dark_red>[<red><bold>CHASSÉ</bold></red><dark_red>]</dark_red> ");
        t.prefix(prefix);
    }

    private void putInHuntedTeam(Player p) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        // Remove from any smp_* team first so glow color isn't overridden.
        for (Team t : main.getTeams()) {
            if (!t.getName().startsWith("smp_")) continue;
            if (t.getName().equals(TEAM_NAME)) continue;
            if (t.hasEntry(p.getName())) t.removeEntry(p.getName());
        }
        Team hunted = main.getTeam(TEAM_NAME);
        if (hunted == null) { ensureHuntedTeam(); hunted = main.getTeam(TEAM_NAME); }
        if (hunted != null && !hunted.hasEntry(p.getName())) hunted.addEntry(p.getName());
    }

    private void removeFromHuntedTeam(Player p) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hunted = main.getTeam(TEAM_NAME);
        if (hunted != null && hunted.hasEntry(p.getName())) hunted.removeEntry(p.getName());
        // Let NametagManager re-place them in their normal team.
        if (plugin.nametags() != null) plugin.nametags().refresh(p);
    }

    // --------------- persistence ---------------

    private void loadState() {
        String today = LocalDate.now().toString();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT date, bounty_used, target_uuid, target_name, target_kills FROM hunted_state WHERE id=1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String date = rs.getString(1);
                    int used = rs.getInt(2);
                    String targetUuid = rs.getString(3);
                    String targetName = rs.getString(4);
                    int targetKills = rs.getInt(5);
                    if (today.equals(date)) {
                        lastBountyDate = used != 0 ? date : null;
                        if (targetUuid != null) {
                            try {
                                currentHunted = UUID.fromString(targetUuid);
                                currentHuntedName = targetName;
                                currentHuntedKills = targetKills;
                            } catch (IllegalArgumentException ignored) {}
                        }
                    } else {
                        // New day — clear hunted + bounty flag, persist.
                        lastBountyDate = null;
                        currentHunted = null;
                        currentHuntedName = null;
                        currentHuntedKills = 0;
                        saveState();
                    }
                } else {
                    lastBountyDate = null;
                    saveState();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("hunted.loadState: " + e.getMessage());
        }
    }

    private void saveState() {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO hunted_state(id, date, bounty_used, target_uuid, target_name, target_kills) " +
                     "VALUES(1, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET date=excluded.date, bounty_used=excluded.bounty_used, " +
                     "target_uuid=excluded.target_uuid, target_name=excluded.target_name, target_kills=excluded.target_kills")) {
            ps.setString(1, currentDate.toString());
            ps.setInt(2, currentDate.toString().equals(lastBountyDate) ? 1 : 0);
            if (currentHunted != null) {
                ps.setString(3, currentHunted.toString());
                ps.setString(4, currentHuntedName);
            } else {
                ps.setNull(3, java.sql.Types.VARCHAR);
                ps.setNull(4, java.sql.Types.VARCHAR);
            }
            ps.setInt(5, currentHuntedKills);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("hunted.saveState: " + e.getMessage());
        }
    }
}
