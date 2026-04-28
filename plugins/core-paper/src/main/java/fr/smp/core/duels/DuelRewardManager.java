package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Handles the post-match outcome: ELO update, win/loss bookkeeping, saph
 * payout to the winner, anti-farm gating.
 *
 * Anti-farm rules (in order):
 *   - same-team kill        → 50 saph, no ELO swing
 *   - 2nd kill of the same victim within last hour → 50 saph
 *   - 3rd+ kill of the same victim within last hour → 0 saph
 *   - otherwise                                     → 100 saph + standard ELO
 */
public class DuelRewardManager {

    private static final long ANTIFARM_WINDOW_MS = 60L * 60 * 1000;  // 1h
    private static final int  REWARD_BASE_SAPH = 100;
    private static final int  REWARD_REDUCED_SAPH = 50;
    private static final int  ELO_K = 32;
    private static final int  ELO_DEFAULT = 1000;

    private final SMPCore plugin;
    private final Database db;

    public DuelRewardManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    private static final int SURRENDER_ELO_PENALTY = 8;

    /** Both players lose a small flat ELO — no winner, no saph. */
    public void applySurrender(UUID a, UUID b) {
        if (a == null || b == null) return;
        applyFlatPenalty(a, SURRENDER_ELO_PENALTY);
        applyFlatPenalty(b, SURRENDER_ELO_PENALTY);
    }

    private void applyFlatPenalty(UUID uuid, int penalty) {
        DuelStats s = statsOf(uuid);
        Player live = Bukkit.getPlayer(uuid);
        String name = live != null ? live.getName() : s.name();
        int newElo = Math.max(0, s.elo() - penalty);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO duel_stats(uuid, name, elo, wins, losses, streak, best_streak, updated_at) " +
                             "VALUES(?,?,?,0,1,0,0,?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, elo=excluded.elo, " +
                             "losses=duel_stats.losses+1, streak=0, updated_at=excluded.updated_at")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, newElo);
            ps.setLong(4, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_stats.flatPenalty: " + e.getMessage());
        }
        if (live != null) {
            live.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<gray>ELO <red>-" + penalty + "</red> <dark_gray>(" + newElo + ")</dark_gray> <gray>(capitulation)</gray>"));
        }
    }

    public void applyOutcome(UUID winner, UUID loser) {
        if (winner == null || loser == null) return;
        boolean sameTeam = isSameTeam(winner, loser);
        int previousKills = killsInLastHour(winner, loser);

        int reward;
        if (sameTeam) {
            reward = REWARD_REDUCED_SAPH;
        } else if (previousKills == 0) {
            reward = REWARD_BASE_SAPH;
        } else if (previousKills == 1) {
            reward = REWARD_REDUCED_SAPH;
        } else {
            reward = 0;
        }

        if (reward > 0) {
            grantSaph(winner, reward, "duel kill");
            Player p = Bukkit.getPlayer(winner);
            if (p != null) {
                p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gray>+<gold>" + reward + "</gold> <gray>saphirs.</gray>"));
            }
        }

        recordKill(winner, loser, sameTeam);

        if (!sameTeam) {
            updateElo(winner, loser);
        } else {
            // Bump win/loss counters, leave ELO alone.
            bumpWin(winner, 0);
            bumpLoss(loser, 0);
        }
    }

    /* ------------------------------ Stats ------------------------------ */

    public java.util.List<DuelStats> top(int limit) {
        java.util.List<DuelStats> out = new java.util.ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, elo, wins, losses, streak, best_streak " +
                             "FROM duel_stats ORDER BY elo DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DuelStats(
                            UUID.fromString(rs.getString(1)), rs.getString(2),
                            rs.getInt(3), rs.getInt(4), rs.getInt(5),
                            rs.getInt(6), rs.getInt(7)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_stats.top: " + e.getMessage());
        }
        return out;
    }

    public DuelStats statsOf(UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, elo, wins, losses, streak, best_streak FROM duel_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DuelStats(uuid, rs.getString(1), rs.getInt(2),
                            rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_stats.get: " + e.getMessage());
        }
        return new DuelStats(uuid, "?", ELO_DEFAULT, 0, 0, 0, 0);
    }

    /* ------------------------------ Internals ------------------------------ */

    private boolean isSameTeam(UUID a, UUID b) {
        if (plugin.players() == null) return false;
        PlayerData pa = plugin.players().get(a);
        if (pa == null) pa = plugin.players().loadOffline(a);
        PlayerData pb = plugin.players().get(b);
        if (pb == null) pb = plugin.players().loadOffline(b);
        if (pa == null || pb == null) return false;
        String ta = pa.teamId();
        String tb = pb.teamId();
        return ta != null && ta.equals(tb);
    }

    private int killsInLastHour(UUID killer, UUID victim) {
        long since = System.currentTimeMillis() - ANTIFARM_WINDOW_MS;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM duel_kill_history WHERE killer=? AND victim=? AND kill_time>=?")) {
            ps.setString(1, killer.toString());
            ps.setString(2, victim.toString());
            ps.setLong(3, since);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_kill_history.count: " + e.getMessage());
        }
        return 0;
    }

    private void recordKill(UUID killer, UUID victim, boolean sameTeam) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO duel_kill_history(killer, victim, kill_time, same_team) VALUES (?,?,?,?)")) {
            ps.setString(1, killer.toString());
            ps.setString(2, victim.toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.setInt(4, sameTeam ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_kill_history.insert: " + e.getMessage());
        }
    }

    private void grantSaph(UUID uuid, int amount, String reason) {
        PlayerData d = plugin.players().get(uuid);
        if (d == null) d = plugin.players().loadOffline(uuid);
        if (d == null) return;
        d.addShards(amount);
        plugin.players().save(d);
        plugin.logs().log(LogCategory.ECONOMY, "duel saph +" + amount + " " + d.name() + " (" + reason + ")");
    }

    private void updateElo(UUID winner, UUID loser) {
        DuelStats sw = statsOf(winner);
        DuelStats sl = statsOf(loser);
        // Standard Elo: expected = 1 / (1 + 10^((opp - me)/400))
        double ew = 1.0 / (1.0 + Math.pow(10, (sl.elo() - sw.elo()) / 400.0));
        double el = 1.0 - ew;
        int gain = (int) Math.round(ELO_K * (1.0 - ew));
        int lossAmt = (int) Math.round(ELO_K * (0.0 - el));
        bumpWin(winner, gain);
        bumpLoss(loser, lossAmt);

        Player wp = Bukkit.getPlayer(winner);
        if (wp != null) {
            wp.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<gray>ELO <green>+" + gain + "</green> <dark_gray>(" + (sw.elo() + gain) + ")</dark_gray>"));
        }
        Player lp = Bukkit.getPlayer(loser);
        if (lp != null) {
            lp.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<gray>ELO <red>" + lossAmt + "</red> <dark_gray>(" + (sl.elo() + lossAmt) + ")</dark_gray>"));
        }
    }

    private void bumpWin(UUID uuid, int eloDelta) {
        DuelStats s = statsOf(uuid);
        Player live = Bukkit.getPlayer(uuid);
        String name = live != null ? live.getName() : s.name();
        int newElo = Math.max(0, s.elo() + eloDelta);
        int newStreak = s.streak() + 1;
        int newBest = Math.max(s.bestStreak(), newStreak);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO duel_stats(uuid, name, elo, wins, losses, streak, best_streak, updated_at) " +
                             "VALUES(?,?,?,1,0,?,?,?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, elo=excluded.elo, " +
                             "wins=duel_stats.wins+1, streak=excluded.streak, best_streak=excluded.best_streak, " +
                             "updated_at=excluded.updated_at")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, newElo);
            ps.setInt(4, newStreak);
            ps.setInt(5, newBest);
            ps.setLong(6, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_stats.bumpWin: " + e.getMessage());
        }
    }

    private void bumpLoss(UUID uuid, int eloDelta) {
        DuelStats s = statsOf(uuid);
        Player live = Bukkit.getPlayer(uuid);
        String name = live != null ? live.getName() : s.name();
        int newElo = Math.max(0, s.elo() + eloDelta);  // delta is negative
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO duel_stats(uuid, name, elo, wins, losses, streak, best_streak, updated_at) " +
                             "VALUES(?,?,?,0,1,0,0,?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, elo=excluded.elo, " +
                             "losses=duel_stats.losses+1, streak=0, updated_at=excluded.updated_at")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, newElo);
            ps.setLong(4, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_stats.bumpLoss: " + e.getMessage());
        }
    }

    public record DuelStats(UUID uuid, String name, int elo, int wins, int losses, int streak, int bestStreak) {}
}
