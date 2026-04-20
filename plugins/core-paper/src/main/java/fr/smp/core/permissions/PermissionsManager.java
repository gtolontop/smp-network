package fr.smp.core.permissions;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mini-LuckPerms: groups + users stored in shared SQLite so both backends see
 * the same data. Any change is persisted and broadcast via plugin messaging so
 * the other backend refreshes live.
 */
public class PermissionsManager {

    public static final String DEFAULT_GROUP = "default";
    public static final String ADMIN_GROUP = "admin";

    public static final class Group {
        public final String name;
        public String prefix = "";
        public int weight = 0;
        public boolean admin = false;
        public final Set<String> permissions = ConcurrentHashMap.newKeySet();
        public final Set<String> parents = ConcurrentHashMap.newKeySet();

        Group(String name) { this.name = name; }
    }

    public static final class User {
        public final UUID uuid;
        public String name;
        public String primaryGroup = DEFAULT_GROUP;
        public final Set<String> permissions = ConcurrentHashMap.newKeySet();

        User(UUID uuid, String name) { this.uuid = uuid; this.name = name; }
    }

    private final SMPCore plugin;
    private final Database db;

    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    // Seed perms for admin. We both flip setOp(true) AND grant these keys so our
    // hasPermission checks resolve without needing to iterate all registered perms.
    private static final String[] ADMIN_SEED_PERMS = {
            "smp.admin",
            "smp.chat.color",
            "smp.chat.format",
            "smp.sync.bypass",
            "smp.rtp.bypass",
            "smp.homes.20",
            "smp.perm.manage",
            "smp.op",
            "bukkit.command.op.give",
            "bukkit.command.op.take",
    };

    public PermissionsManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ---- lifecycle ----

    public void load() {
        ensureSchema();
        seedDefaults();
        reload();
    }

    public void reload() {
        Map<String, Group> freshGroups = new ConcurrentHashMap<>();
        Map<UUID, User> freshUsers = new ConcurrentHashMap<>();
        try (Connection c = db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name, prefix, weight, admin FROM perm_groups");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = new Group(rs.getString(1));
                    g.prefix = rs.getString(2) == null ? "" : rs.getString(2);
                    g.weight = rs.getInt(3);
                    g.admin = rs.getInt(4) != 0;
                    freshGroups.put(g.name, g);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT group_name, perm FROM perm_group_perms");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = freshGroups.get(rs.getString(1));
                    if (g != null) g.permissions.add(rs.getString(2));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT group_name, parent FROM perm_group_parents");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = freshGroups.get(rs.getString(1));
                    if (g != null) g.parents.add(rs.getString(2));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT uuid, name, primary_group FROM perm_users");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID u = UUID.fromString(rs.getString(1));
                    User user = new User(u, rs.getString(2));
                    user.primaryGroup = rs.getString(3);
                    if (user.primaryGroup == null) user.primaryGroup = DEFAULT_GROUP;
                    freshUsers.put(u, user);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT uuid, perm FROM perm_user_perms");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User u = freshUsers.get(UUID.fromString(rs.getString(1)));
                    if (u != null) u.permissions.add(rs.getString(2));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("PermissionsManager.reload failed: " + e.getMessage());
            return;
        }
        groups.clear(); groups.putAll(freshGroups);
        users.clear(); users.putAll(freshUsers);

        // Re-apply on every online player.
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    private void ensureSchema() {
        String[] stmts = {
                "CREATE TABLE IF NOT EXISTS perm_groups (name TEXT PRIMARY KEY, prefix TEXT, weight INTEGER NOT NULL DEFAULT 0, admin INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS perm_group_perms (group_name TEXT NOT NULL, perm TEXT NOT NULL, PRIMARY KEY(group_name, perm))",
                "CREATE TABLE IF NOT EXISTS perm_group_parents (group_name TEXT NOT NULL, parent TEXT NOT NULL, PRIMARY KEY(group_name, parent))",
                "CREATE TABLE IF NOT EXISTS perm_users (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, primary_group TEXT NOT NULL DEFAULT 'default')",
                "CREATE TABLE IF NOT EXISTS perm_user_perms (uuid TEXT NOT NULL, perm TEXT NOT NULL, PRIMARY KEY(uuid, perm))"
        };
        try (Connection c = db.get();
             java.sql.Statement st = c.createStatement()) {
            for (String s : stmts) st.execute(s);
        } catch (SQLException e) {
            plugin.getLogger().severe("Perm schema: " + e.getMessage());
        }
    }

    private void seedDefaults() {
        try (Connection c = db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO perm_groups(name, prefix, weight, admin) VALUES(?,?,?,?)")) {
                ps.setString(1, DEFAULT_GROUP); ps.setString(2, ""); ps.setInt(3, 0); ps.setInt(4, 0); ps.executeUpdate();
                ps.setString(1, ADMIN_GROUP); ps.setString(2, "<red>[Admin]</red> "); ps.setInt(3, 100); ps.setInt(4, 1); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO perm_group_perms(group_name, perm) VALUES(?,?)")) {
                for (String p : ADMIN_SEED_PERMS) {
                    ps.setString(1, ADMIN_GROUP); ps.setString(2, p); ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Perm seed: " + e.getMessage());
        }
    }

    // ---- apply ----

    public void apply(Player player) {
        // Import vanilla ops on first touch: if the player is currently OP but
        // has no perm record yet, promote them to admin so we don't silently
        // strip their rights during the migration.
        boolean wasKnown = users.containsKey(player.getUniqueId());
        User u = getOrCreateUser(player.getUniqueId(), player.getName());
        if (!wasKnown && player.isOp() && DEFAULT_GROUP.equals(u.primaryGroup)) {
            setUserGroup(player.getUniqueId(), player.getName(), ADMIN_GROUP);
            u = users.get(player.getUniqueId());
        }
        // detach old
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try { player.removeAttachment(old); } catch (IllegalArgumentException ignored) {}
        }
        PermissionAttachment att = player.addAttachment(plugin);

        Set<String> effective = resolvePermissions(u);
        boolean admin = isEffectivelyAdmin(u);
        for (String p : effective) att.setPermission(p, true);
        attachments.put(player.getUniqueId(), att);

        // Vanilla op sync: admin group -> OP true, otherwise OP false.
        if (player.isOp() != admin) player.setOp(admin);

        player.recalculatePermissions();
    }

    public void detach(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try { player.removeAttachment(old); } catch (IllegalArgumentException ignored) {}
        }
    }

    private Set<String> resolvePermissions(User u) {
        Set<String> out = new HashSet<>(u.permissions);
        Group g = groups.get(u.primaryGroup);
        if (g != null) collectGroupPerms(g, out, new HashSet<>());
        return out;
    }

    private void collectGroupPerms(Group g, Set<String> out, Set<String> visited) {
        if (!visited.add(g.name)) return;
        out.addAll(g.permissions);
        for (String parent : g.parents) {
            Group pg = groups.get(parent);
            if (pg != null) collectGroupPerms(pg, out, visited);
        }
    }

    private boolean isEffectivelyAdmin(User u) {
        Group g = groups.get(u.primaryGroup);
        if (g == null) return false;
        return isAdminRec(g, new HashSet<>());
    }

    private boolean isAdminRec(Group g, Set<String> visited) {
        if (!visited.add(g.name)) return false;
        if (g.admin) return true;
        for (String p : g.parents) {
            Group pg = groups.get(p);
            if (pg != null && isAdminRec(pg, visited)) return true;
        }
        return false;
    }

    // ---- accessors ----

    public Group getGroup(String name) { return groups.get(name); }
    public Collection<Group> groups() { return groups.values(); }
    public Collection<User> users() { return users.values(); }

    public User getUser(UUID uuid) { return users.get(uuid); }

    /** Combined prefix (group prefix) for chat / tab. */
    public String prefixOf(UUID uuid) {
        User u = users.get(uuid);
        if (u == null) return "";
        Group g = groups.get(u.primaryGroup);
        return g == null || g.prefix == null ? "" : g.prefix;
    }

    public String primaryGroup(UUID uuid) {
        User u = users.get(uuid);
        return u == null ? DEFAULT_GROUP : u.primaryGroup;
    }

    public User getOrCreateUser(UUID uuid, String name) {
        User u = users.get(uuid);
        if (u != null) {
            if (name != null && !name.equals(u.name)) {
                u.name = name;
                try (Connection c = db.get();
                     PreparedStatement ps = c.prepareStatement("UPDATE perm_users SET name=? WHERE uuid=?")) {
                    ps.setString(1, name); ps.setString(2, uuid.toString()); ps.executeUpdate();
                } catch (SQLException ignored) {}
            }
            return u;
        }
        u = new User(uuid, name == null ? "unknown" : name);
        users.put(uuid, u);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO perm_users(uuid, name, primary_group) VALUES(?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, u.name);
            ps.setString(3, u.primaryGroup);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        return u;
    }

    // ---- group mutations ----

    public boolean createGroup(String name) {
        if (groups.containsKey(name)) return false;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO perm_groups(name, prefix, weight, admin) VALUES(?,?,?,?)")) {
            ps.setString(1, name); ps.setString(2, ""); ps.setInt(3, 0); ps.setInt(4, 0);
            ps.executeUpdate();
        } catch (SQLException e) { return false; }
        groups.put(name, new Group(name));
        broadcastReload();
        return true;
    }

    public boolean deleteGroup(String name) {
        if (DEFAULT_GROUP.equals(name) || ADMIN_GROUP.equals(name)) return false;
        if (!groups.containsKey(name)) return false;
        try (Connection c = db.get()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM perm_group_perms WHERE group_name=?")) {
                ps.setString(1, name); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM perm_group_parents WHERE group_name=? OR parent=?")) {
                ps.setString(1, name); ps.setString(2, name); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE perm_users SET primary_group=? WHERE primary_group=?")) {
                ps.setString(1, DEFAULT_GROUP); ps.setString(2, name); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM perm_groups WHERE name=?")) {
                ps.setString(1, name); ps.executeUpdate();
            }
        } catch (SQLException e) { return false; }
        reload();
        broadcastReload();
        return true;
    }

    public boolean setGroupPrefix(String group, String prefix) {
        Group g = groups.get(group);
        if (g == null) return false;
        g.prefix = prefix == null ? "" : prefix;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE perm_groups SET prefix=? WHERE name=?")) {
            ps.setString(1, g.prefix); ps.setString(2, group); ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    public boolean addGroupPerm(String group, String perm) {
        Group g = groups.get(group);
        if (g == null) return false;
        g.permissions.add(perm);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO perm_group_perms(group_name, perm) VALUES(?,?)")) {
            ps.setString(1, group); ps.setString(2, perm); ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    public boolean delGroupPerm(String group, String perm) {
        Group g = groups.get(group);
        if (g == null) return false;
        g.permissions.remove(perm);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM perm_group_perms WHERE group_name=? AND perm=?")) {
            ps.setString(1, group); ps.setString(2, perm); ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    public boolean addGroupParent(String group, String parent) {
        Group g = groups.get(group);
        Group pg = groups.get(parent);
        if (g == null || pg == null || g == pg) return false;
        g.parents.add(parent);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO perm_group_parents(group_name, parent) VALUES(?,?)")) {
            ps.setString(1, group); ps.setString(2, parent); ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    public boolean delGroupParent(String group, String parent) {
        Group g = groups.get(group);
        if (g == null) return false;
        g.parents.remove(parent);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM perm_group_parents WHERE group_name=? AND parent=?")) {
            ps.setString(1, group); ps.setString(2, parent); ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    // ---- user mutations ----

    public boolean setUserGroup(UUID uuid, String name, String group) {
        if (!groups.containsKey(group)) return false;
        User u = getOrCreateUser(uuid, name);
        u.primaryGroup = group;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO perm_users(uuid, name, primary_group) VALUES(?,?,?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, primary_group=excluded.primary_group")) {
            ps.setString(1, uuid.toString()); ps.setString(2, u.name); ps.setString(3, group);
            ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    public boolean addUserPerm(UUID uuid, String name, String perm) {
        User u = getOrCreateUser(uuid, name);
        u.permissions.add(perm);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO perm_user_perms(uuid, perm) VALUES(?,?)")) {
            ps.setString(1, uuid.toString()); ps.setString(2, perm); ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    public boolean delUserPerm(UUID uuid, String perm) {
        User u = users.get(uuid);
        if (u != null) u.permissions.remove(perm);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM perm_user_perms WHERE uuid=? AND perm=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, perm); ps.executeUpdate();
        } catch (SQLException e) { return false; }
        broadcastReload();
        return true;
    }

    private void broadcastReload() {
        // Re-apply locally now; push a plugin-message so the other backend does the same.
        Bukkit.getScheduler().runTask(plugin, () -> {
            reload();
        });
        if (plugin.getMessageChannel() != null) {
            plugin.getMessageChannel().sendPermReload();
        }
    }
}
