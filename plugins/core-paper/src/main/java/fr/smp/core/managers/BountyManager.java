package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.storage.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BountyManager {

    public record Bounty(UUID target, String targetName, double amount,
                         UUID lastIssuer, String lastIssuerName,
                         int contributors, long createdAt, long updatedAt) {}

    public record Contribution(UUID issuer, String issuerName, double amount) {}

    private final SMPCore plugin;
    private final Database db;

    public BountyManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public double minAmount() {
        return plugin.getConfig().getDouble("bounty.min-amount", 1_000_000.0);
    }

    public double maxAmount() {
        return plugin.getConfig().getDouble("bounty.max-amount", 100_000_000_000.0);
    }

    public Bounty get(UUID target) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT target_uuid, target_name, amount, last_issuer, last_issuer_name, contributors, created_at, updated_at FROM bounties WHERE target_uuid=?")) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return read(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("bounty.get: " + e.getMessage());
        }
        return null;
    }

    public List<Bounty> top(int limit) {
        List<Bounty> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT target_uuid, target_name, amount, last_issuer, last_issuer_name, contributors, created_at, updated_at FROM bounties WHERE amount>0 ORDER BY amount DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("bounty.top: " + e.getMessage());
        }
        return out;
    }

    public int count() {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM bounties WHERE amount>0")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    /** Adds {@code amount} to the bounty on {@code target}. Returns the new total, or -1 on error. */
    public double add(UUID target, String targetName, UUID issuer, String issuerName, double amount) {
        long now = System.currentTimeMillis();
        try (Connection c = db.get()) {
            c.setAutoCommit(false);
            try {
                double current = 0;
                int contributors = 0;
                long createdAt = now;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT amount, contributors, created_at FROM bounties WHERE target_uuid=?")) {
                    ps.setString(1, target.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            current = rs.getDouble(1);
                            contributors = rs.getInt(2);
                            createdAt = rs.getLong(3);
                        }
                    }
                }
                double total = current + amount;

                boolean newContributor;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM bounty_contributions WHERE target_uuid=? AND issuer_uuid=?")) {
                    ps.setString(1, target.toString());
                    ps.setString(2, issuer.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        newContributor = !rs.next();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bounty_contributions(target_uuid, issuer_uuid, issuer_name, amount, updated_at) " +
                        "VALUES(?,?,?,?,?) " +
                        "ON CONFLICT(target_uuid, issuer_uuid) DO UPDATE SET " +
                        "issuer_name=excluded.issuer_name, amount=amount+excluded.amount, updated_at=excluded.updated_at")) {
                    ps.setString(1, target.toString());
                    ps.setString(2, issuer.toString());
                    ps.setString(3, issuerName);
                    ps.setDouble(4, amount);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }
                int newContribCount = contributors + (newContributor ? 1 : 0);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bounties(target_uuid, target_name, amount, last_issuer, last_issuer_name, contributors, created_at, updated_at) " +
                        "VALUES(?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(target_uuid) DO UPDATE SET target_name=excluded.target_name, amount=excluded.amount, " +
                        "last_issuer=excluded.last_issuer, last_issuer_name=excluded.last_issuer_name, " +
                        "contributors=excluded.contributors, updated_at=excluded.updated_at")) {
                    ps.setString(1, target.toString());
                    ps.setString(2, targetName);
                    ps.setDouble(3, total);
                    ps.setString(4, issuer.toString());
                    ps.setString(5, issuerName);
                    ps.setInt(6, newContribCount);
                    ps.setLong(7, createdAt);
                    ps.setLong(8, now);
                    ps.executeUpdate();
                }
                c.commit();
                plugin.logs().log(LogCategory.ECONOMY,
                        "bounty.add target=" + targetName + " by=" + issuerName + " +" + amount + " total=" + total);
                return total;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("bounty.add: " + e.getMessage());
            return -1;
        }
    }

    public void remove(UUID target) {
        try (Connection c = db.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bounties WHERE target_uuid=?")) {
                    ps.setString(1, target.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bounty_contributions WHERE target_uuid=?")) {
                    ps.setString(1, target.toString());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("bounty.remove: " + e.getMessage());
        }
    }

    public List<Contribution> topContributors(UUID target, int limit) {
        List<Contribution> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT issuer_uuid, issuer_name, amount FROM bounty_contributions " +
                     "WHERE target_uuid=? ORDER BY amount DESC LIMIT ?")) {
            ps.setString(1, target.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Contribution(
                            UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            rs.getDouble(3)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("bounty.topContributors: " + e.getMessage());
        }
        return out;
    }

    public Contribution biggestContributor(UUID target) {
        List<Contribution> list = topContributors(target, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    private Bounty read(ResultSet rs) throws SQLException {
        UUID target = UUID.fromString(rs.getString(1));
        String targetName = rs.getString(2);
        double amount = rs.getDouble(3);
        String lastIssuerStr = rs.getString(4);
        UUID lastIssuer = lastIssuerStr != null ? UUID.fromString(lastIssuerStr) : null;
        String lastIssuerName = rs.getString(5);
        int contributors = rs.getInt(6);
        long createdAt = rs.getLong(7);
        long updatedAt = rs.getLong(8);
        return new Bounty(target, targetName, amount, lastIssuer, lastIssuerName, contributors, createdAt, updatedAt);
    }
}
