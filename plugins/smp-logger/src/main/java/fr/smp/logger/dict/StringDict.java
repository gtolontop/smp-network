package fr.smp.logger.dict;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interns chat lines / commands / sign text / anvil renames as compact ids.
 * Indexed by a 32-bit content hash so repeated messages (logout spam, common
 * phrases) cost ONE row total. Lookup tries the hash bucket first.
 *
 * Capped LRU cache in memory so very chatty servers don't OOM.
 */
public class StringDict {

    private static final int CACHE_LIMIT = 4096;

    private final SMPLogger plugin;
    private final Database db;
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();
    private final Map<Integer, String> reverse = new ConcurrentHashMap<>();

    public StringDict(SMPLogger plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public int idOf(String text) {
        if (text == null) return 0;
        String trimmed = text.length() > 4096 ? text.substring(0, 4096) : text;
        Integer cached = cache.get(trimmed);
        if (cached != null) return cached;
        return lookupOrInsert(trimmed);
    }

    public String textOf(int id) {
        if (id == 0) return null;
        String cached = reverse.get(id);
        if (cached != null) return cached;
        try (Connection c = db.reader();
             PreparedStatement ps = c.prepareStatement("SELECT content FROM dict_strings WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String s = rs.getString(1);
                    if (reverse.size() < CACHE_LIMIT) reverse.put(id, s);
                    return s;
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private synchronized int lookupOrInsert(String text) {
        Integer cached = cache.get(text);
        if (cached != null) return cached;
        int hash = text.hashCode();
        try (Connection c = db.writer();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, content FROM dict_strings WHERE hash = ?")) {
            ps.setInt(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (text.equals(rs.getString(2))) {
                        int id = rs.getInt(1);
                        if (cache.size() < CACHE_LIMIT) cache.put(text, id);
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StringDict select failed: " + e.getMessage());
            return 0;
        }
        try (Connection c = db.writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dict_strings(hash, content) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, hash);
            ps.setString(2, text);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    if (cache.size() < CACHE_LIMIT) cache.put(text, id);
                    return id;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StringDict insert failed: " + e.getMessage());
        }
        return 0;
    }
}
