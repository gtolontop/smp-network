package fr.smp.logger.items;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.db.Database;
import fr.smp.logger.dict.MaterialDict;
import fr.smp.logger.util.Compression;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedup store for precious-item NBT.
 *  - Hot path: hash already known → INCREMENT ref_count, return the hash.
 *  - Cold path: serialize + DEFLATE + INSERT, then return the hash.
 *
 * The "hash → known" check is cached in memory (Set<Hash>) so repeat events
 * for the same item never touch the DB except for the ref_count UPDATE.
 */
public class PreciousStore {

    private final SMPLogger plugin;
    private final Database db;
    private final MaterialDict materials;
    private final PreciousDetector detector;
    private final ConcurrentHashMap<HashKey, Boolean> known = new ConcurrentHashMap<>();
    private final AtomicLong inserts = new AtomicLong();
    private final AtomicLong dedupHits = new AtomicLong();

    public PreciousStore(SMPLogger plugin, Database db, MaterialDict materials, PreciousDetector detector) {
        this.plugin = plugin;
        this.db = db;
        this.materials = materials;
        this.detector = detector;
    }

    public PreciousDetector detector() { return detector; }

    /** Returns the 16-byte hash to embed in the event row, or null if non-precious. */
    public byte[] storeIfPrecious(ItemStack item) {
        if (item == null || !detector.isPrecious(item)) return null;
        ItemHasher.Canonical c = ItemHasher.canonical(item);
        HashKey key = new HashKey(c.hash());
        if (known.containsKey(key)) {
            dedupHits.incrementAndGet();
            bumpRef(c.hash());
            return c.hash();
        }
        // Need to confirm against DB before inserting (cache may have been cleared).
        try (Connection con = db.writer();
             PreparedStatement select = con.prepareStatement(
                     "SELECT 1 FROM precious_items WHERE hash = ?")) {
            select.setBytes(1, c.hash());
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    known.put(key, Boolean.TRUE);
                    dedupHits.incrementAndGet();
                    bumpRef(c.hash());
                    return c.hash();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PreciousStore select failed: " + e.getMessage());
        }
        insertNew(c, item);
        known.put(key, Boolean.TRUE);
        return c.hash();
    }

    private void insertNew(ItemHasher.Canonical c, ItemStack item) {
        byte[] zlib = Compression.deflate(c.payload());
        try (Connection con = db.writer();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO precious_items"
                             + "(hash, kind, material_id, summary, nbt_zlib, nbt_size, first_seen, ref_count)"
                             + " VALUES (?, ?, ?, ?, ?, ?, ?, 1)"
                             + " ON CONFLICT(hash) DO UPDATE SET ref_count = ref_count + 1")) {
            ps.setBytes(1, c.hash());
            ps.setString(2, classify(item));
            ps.setInt(3, materials.idOf(item.getType()));
            ps.setString(4, detector.summarize(item));
            ps.setBytes(5, zlib);
            ps.setInt(6, c.payload().length);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
            inserts.incrementAndGet();
        } catch (SQLException e) {
            plugin.getLogger().warning("PreciousStore insert failed: " + e.getMessage());
        }
    }

    private void bumpRef(byte[] hash) {
        try (Connection con = db.writer();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE precious_items SET ref_count = ref_count + 1 WHERE hash = ?")) {
            ps.setBytes(1, hash);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static String classify(ItemStack item) {
        String n = item.getType().name();
        if (n.endsWith("_BOOK") || n.equals("WRITTEN_BOOK") || n.equals("WRITABLE_BOOK") || n.equals("ENCHANTED_BOOK")) return "BOOK";
        if (n.endsWith("SHULKER_BOX")) return "SHULKER";
        if (n.equals("PLAYER_HEAD") || n.endsWith("_HEAD")) return "HEAD";
        if (n.equals("ELYTRA")) return "ELYTRA";
        if (n.startsWith("NETHERITE_")) return "NETHERITE";
        if (n.startsWith("MUSIC_DISC_")) return "DISC";
        if (n.equals("FILLED_MAP") || n.equals("MAP")) return "MAP";
        if (n.equals("SPAWNER") || n.equals("TRIAL_SPAWNER")) return "SPAWNER";
        return "NAMED";
    }

    /** Reverse a stored hash back to the live ItemStack (for /inspect output). */
    public ItemStack restore(byte[] hash) {
        try (Connection con = db.reader();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT nbt_zlib FROM precious_items WHERE hash = ?")) {
            ps.setBytes(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] payload = Compression.inflate(rs.getBytes(1));
                    try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(payload))) {
                        return (ItemStack) in.readObject();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("PreciousStore restore failed: " + e.getMessage());
        }
        return null;
    }

    public long inserts() { return inserts.get(); }
    public long dedupHits() { return dedupHits.get(); }

    /** Wraps the 16-byte hash as a Map key. */
    private record HashKey(byte[] bytes) {
        @Override public boolean equals(Object o) {
            return o instanceof HashKey k && Arrays.equals(bytes, k.bytes);
        }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }
}
