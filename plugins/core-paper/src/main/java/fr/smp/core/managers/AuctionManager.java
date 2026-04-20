package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.storage.Database;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuctionManager {

    public record Listing(long id, UUID seller, String sellerName,
                          ItemStack item, double price,
                          long listedAt, long expiresAt) {}

    private final SMPCore plugin;
    private final Database db;

    public AuctionManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public long listingTtlMs() {
        return plugin.getConfig().getLong("auction.ttl-hours", 48) * 3600_000L;
    }

    public int maxPerPlayer() {
        return plugin.getConfig().getInt("auction.max-per-player", 5);
    }

    public double feePercent() {
        return plugin.getConfig().getDouble("auction.fee-percent", 5.0);
    }

    public int countActive(UUID seller) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM auctions WHERE seller=? AND sold=0 AND expires_at>?")) {
            ps.setString(1, seller.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    public long list(UUID seller, String sellerName, ItemStack stack, double price) {
        byte[] data;
        try {
            data = serialize(stack);
        } catch (Exception e) {
            plugin.getLogger().warning("auction.serialize: " + e.getMessage());
            return -1;
        }
        long now = System.currentTimeMillis();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO auctions(seller, seller_name, item_data, price, listed_at, expires_at) VALUES(?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.toString());
            ps.setString(2, sellerName);
            ps.setBytes(3, data);
            ps.setDouble(4, price);
            ps.setLong(5, now);
            ps.setLong(6, now + listingTtlMs());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    plugin.logs().log(LogCategory.AUCTION, "list id=" + id + " seller=" + sellerName + " price=" + price);
                    return id;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("auction.list: " + e.getMessage());
        }
        return -1;
    }

    public List<Listing> active() {
        List<Listing> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, seller, seller_name, item_data, price, listed_at, expires_at FROM auctions WHERE sold=0 AND expires_at>? ORDER BY listed_at DESC LIMIT 200")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack it;
                    try { it = deserialize(rs.getBytes(4)); } catch (Exception e) { continue; }
                    out.add(new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)),
                            rs.getString(3), it, rs.getDouble(5),
                            rs.getLong(6), rs.getLong(7)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("auction.active: " + e.getMessage());
        }
        return out;
    }

    public List<Listing> ofSeller(UUID seller) {
        List<Listing> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, seller, seller_name, item_data, price, listed_at, expires_at FROM auctions WHERE seller=? AND sold=0 ORDER BY listed_at DESC")) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack it;
                    try { it = deserialize(rs.getBytes(4)); } catch (Exception e) { continue; }
                    out.add(new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)),
                            rs.getString(3), it, rs.getDouble(5),
                            rs.getLong(6), rs.getLong(7)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("auction.ofSeller: " + e.getMessage());
        }
        return out;
    }

    public Listing get(long id) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, seller, seller_name, item_data, price, listed_at, expires_at FROM auctions WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ItemStack it = deserialize(rs.getBytes(4));
                    return new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)),
                            rs.getString(3), it, rs.getDouble(5),
                            rs.getLong(6), rs.getLong(7));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("auction.get: " + e.getMessage());
        }
        return null;
    }

    public boolean markSold(long id) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE auctions SET sold=1 WHERE id=? AND sold=0")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public void remove(long id) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM auctions WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private byte[] serialize(ItemStack stack) throws IOException {
        byte[] nbt = stack.serializeAsBytes();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeInt(nbt.length);
            dos.write(nbt);
        }
        return bos.toByteArray();
    }

    private ItemStack deserialize(byte[] data) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            int len = dis.readInt();
            byte[] nbt = new byte[len];
            dis.readFully(nbt);
            return ItemStack.deserializeBytes(nbt);
        }
    }
}
