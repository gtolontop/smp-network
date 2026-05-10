package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

public class BountyAntiAbuseManager {

    public record Verdict(boolean allowed, String reason, String detail) {
        public static Verdict allow() {
            return new Verdict(true, "", "");
        }

        public static Verdict block(String reason, String detail) {
            return new Verdict(false, reason, detail == null ? "" : detail);
        }
    }

    private record RelationMatch(int score, String level) {}

    private final SMPCore plugin;

    public BountyAntiAbuseManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public Verdict canPlace(Player issuer, UUID target, String targetName) {
        if (!enabled() || issuer == null || target == null) return Verdict.allow();
        if (sameTeam(issuer.getUniqueId(), target)) {
            return Verdict.block("same-team", "same team as " + targetName);
        }
        RelationMatch relation = suspiciousRelation(issuer.getUniqueId(), target);
        if (relation != null) {
            return Verdict.block("hidden-relation",
                    "relation " + relation.level() + " score=" + relation.score() + " with " + targetName);
        }
        return Verdict.allow();
    }

    public Verdict canClaim(Player victim, Player killer) {
        if (!enabled() || victim == null || killer == null) return Verdict.allow();
        if (sameTeam(victim.getUniqueId(), killer.getUniqueId())) {
            return Verdict.block("same-team", killer.getName() + " and " + victim.getName() + " are in the same team");
        }

        RelationMatch direct = suspiciousRelation(victim.getUniqueId(), killer.getUniqueId());
        if (direct != null) {
            return Verdict.block("hidden-relation",
                    killer.getName() + " and " + victim.getName() + " relation "
                            + direct.level() + " score=" + direct.score());
        }

        if (nearbyTeamupEnabled()) {
            Verdict nearby = nearbyTeamup(victim, killer);
            if (!nearby.allowed()) return nearby;
        }
        return Verdict.allow();
    }

    private Verdict nearbyTeamup(Player victim, Player killer) {
        Location death = victim.getLocation();
        if (death.getWorld() == null) return Verdict.allow();
        double radius = Math.max(2.0, plugin.getConfig().getDouble("bounty.anti-abuse.teamup-radius-blocks", 18.0));
        double radiusSq = radius * radius;
        for (Player nearby : death.getWorld().getPlayers()) {
            if (!nearby.isOnline()) continue;
            if (nearby.equals(victim) || nearby.equals(killer)) continue;
            if (nearby.getLocation().distanceSquared(death) > radiusSq) continue;

            if (sameTeam(nearby.getUniqueId(), killer.getUniqueId())) {
                return Verdict.block("nearby-killer-team",
                        nearby.getName() + " was within " + Math.round(radius) + " blocks and is in the killer team");
            }
            RelationMatch killerRelation = suspiciousRelation(nearby.getUniqueId(), killer.getUniqueId());
            if (killerRelation != null) {
                return Verdict.block("nearby-killer-relation",
                        nearby.getName() + " was within " + Math.round(radius) + " blocks and linked to killer "
                                + killerRelation.level() + " score=" + killerRelation.score());
            }
            RelationMatch victimRelation = suspiciousRelation(nearby.getUniqueId(), victim.getUniqueId());
            if (victimRelation != null && sameTeamOrRelatedToKiller(nearby.getUniqueId(), killer.getUniqueId())) {
                return Verdict.block("nearby-teamup",
                        nearby.getName() + " was close and linked around the kill "
                                + victimRelation.level() + " score=" + victimRelation.score());
            }
        }
        return Verdict.allow();
    }

    private boolean sameTeamOrRelatedToKiller(UUID nearby, UUID killer) {
        if (sameTeam(nearby, killer)) return true;
        return suspiciousRelation(nearby, killer) != null;
    }

    private boolean sameTeam(UUID a, UUID b) {
        if (!plugin.getConfig().getBoolean("bounty.anti-abuse.block-same-team", true)) return false;
        if (a == null || b == null || plugin.teams() == null) return false;
        String ta = plugin.teams().teamOf(a);
        String tb = plugin.teams().teamOf(b);
        return ta != null && !ta.isBlank() && ta.equals(tb);
    }

    private RelationMatch suspiciousRelation(UUID a, UUID b) {
        if (!plugin.getConfig().getBoolean("bounty.anti-abuse.hidden-relations.enabled", true)) return null;
        if (a == null || b == null || a.equals(b)) return null;
        File dbFile = loggerDbFile();
        if (!dbFile.exists()) return null;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA query_only=ON");
                s.execute("PRAGMA busy_timeout=1000");
            }
            int aId = loggerPlayerId(c, a);
            int bId = loggerPlayerId(c, b);
            if (aId <= 0 || bId <= 0) return null;
            String pairKey = Math.min(aId, bId) + ":" + Math.max(aId, bId);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT score, level FROM relation_pairs WHERE pair_key=?")) {
                ps.setString(1, pairKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    int score = rs.getInt("score");
                    String level = rs.getString("level");
                    int minLevelRank = levelRank(relationLevelThreshold());
                    if (minLevelRank <= 0) minLevelRank = levelRank("SUSPECT");
                    if (score >= relationScoreThreshold() || levelRank(level) >= minLevelRank) {
                        return new RelationMatch(score, level == null ? "UNKNOWN" : level);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("bounty anti-abuse relation lookup failed: " + e.getMessage());
        }
        return null;
    }

    private int loggerPlayerId(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM dict_players WHERE uuid=?")) {
            ps.setBytes(1, uuidToBytes(uuid));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private File loggerDbFile() {
        String configured = plugin.getConfig().getString(
                "bounty.anti-abuse.hidden-relations.db-file",
                "../shared-data/smplogger/smplogger.db");
        File file = new File(configured);
        if (file.isAbsolute()) return file;
        return new File(plugin.getServer().getWorldContainer(), configured);
    }

    private int relationScoreThreshold() {
        return Math.max(1, plugin.getConfig().getInt("bounty.anti-abuse.hidden-relations.min-score", 35));
    }

    private String relationLevelThreshold() {
        return plugin.getConfig().getString("bounty.anti-abuse.hidden-relations.min-level", "SUSPECT");
    }

    private int levelRank(String level) {
        if (level == null) return 0;
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "WATCH" -> 1;
            case "SUSPECT" -> 2;
            case "PROBABLE" -> 3;
            case "CONFIRMED" -> 4;
            default -> 0;
        };
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("bounty.anti-abuse.enabled", true);
    }

    private boolean nearbyTeamupEnabled() {
        return plugin.getConfig().getBoolean("bounty.anti-abuse.block-nearby-teamup", true);
    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
