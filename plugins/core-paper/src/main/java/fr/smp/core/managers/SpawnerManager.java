package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.SpawnerGUI;
import fr.smp.core.storage.Database;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les spawners custom:
 *  - stockage SQLite (spawners + spawner_loot)
 *  - tick périodique qui génère la loot selon le type et le stack
 *  - pickup / placement / merge-stack
 *  - persistance dirty-flag
 *
 * Uniquement actif sur les backends de type survival.
 */
public class SpawnerManager {

    /** Cap global sur le nombre d'items stockés dans un même spawner. */
    public static final int MAX_STORAGE_PER_SPAWNER = 64 * 64 * 2; // 8192
    /** Cap sur le stack de spawners fusionnés. */
    public static final int MAX_STACK = 1000;
    /** Période du tick (en secondes). */
    public static final int TICK_PERIOD_SEC = 10;

    private final SMPCore plugin;
    private final Database db;
    private final Random random = new Random();

    /** Map world:x,y,z → état. Concurrent pour tolérer le thread de save. */
    private final Map<String, Spawner> spawners = new ConcurrentHashMap<>();

    private final NamespacedKey typeKey;
    private final NamespacedKey stackKey;
    private final NamespacedKey markerKey;

    private BukkitTask tickTask;
    private BukkitTask saveTask;

    public SpawnerManager(SMPCore plugin) {
        this.plugin = plugin;
        this.db = plugin.database();
        this.typeKey = new NamespacedKey(plugin, "spawner_type");
        this.stackKey = new NamespacedKey(plugin, "spawner_stack");
        this.markerKey = new NamespacedKey(plugin, "spawner_marker");
    }

    public NamespacedKey typeKey() { return typeKey; }
    public NamespacedKey stackKey() { return stackKey; }
    public NamespacedKey markerKey() { return markerKey; }

    // =================== LIFECYCLE ===================

    public void start() {
        loadAll();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll,
                TICK_PERIOD_SEC * 20L, TICK_PERIOD_SEC * 20L);
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveDirty,
                30 * 20L, 30 * 20L);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
        saveAll();
    }

    // =================== LOOKUP ===================

    private String key(String world, int x, int y, int z) {
        return world + ":" + x + "," + y + "," + z;
    }

    private String key(World w, int x, int y, int z) {
        return key(w.getName(), x, y, z);
    }

    private String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Spawner at(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return spawners.get(key(loc));
    }

    public Spawner at(Block b) {
        if (b == null) return null;
        return spawners.get(key(b.getWorld(), b.getX(), b.getY(), b.getZ()));
    }

    // =================== PLACE / BREAK / STACK ===================

    /**
     * Enregistre un nouveau spawner au lieu donné. Si un spawner existe déjà
     * juste en dessous, fusionne le stack dessus à la place et retourne true
     * (dans ce cas le caller doit annuler le BlockPlaceEvent). Sinon, crée
     * l'entrée et retourne false.
     */
    public boolean placeOrMerge(Location loc, SpawnerType type, int stack) {
        World w = loc.getWorld();
        Block below = w.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        Spawner existing = at(below);
        if (existing != null && existing.type == type) {
            int newStack = Math.min(MAX_STACK, existing.stack + Math.max(1, stack));
            existing.stack = newStack;
            existing.markDirty();
            return true;
        }
        Block above = w.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        Spawner existingAbove = at(above);
        if (existingAbove != null && existingAbove.type == type) {
            int newStack = Math.min(MAX_STACK, existingAbove.stack + Math.max(1, stack));
            existingAbove.stack = newStack;
            existingAbove.markDirty();
            return true;
        }

        Spawner s = new Spawner();
        s.world = w.getName();
        s.x = loc.getBlockX();
        s.y = loc.getBlockY();
        s.z = loc.getBlockZ();
        s.type = type;
        s.stack = Math.max(1, Math.min(MAX_STACK, stack));
        s.loot = new EnumMap<>(Material.class);
        s.dbId = -1;
        s.markDirty();
        spawners.put(key(w, s.x, s.y, s.z), s);
        return false;
    }

    /**
     * Retire le spawner à cet emplacement (break). Renvoie l'état qui y était
     * pour que le caller puisse donner l'item / drop le contenu.
     */
    public Spawner remove(Location loc) {
        Spawner s = spawners.remove(key(loc));
        if (s != null) {
            closeViewers(s);
            deleteFromDb(s);
        }
        return s;
    }

    /** Ferme toute GUI ouverte sur ce spawner (ex: au break). */
    public void closeViewers(Spawner s) {
        if (s == null) return;
        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
            var top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof SpawnerGUI sg && sg.spawner() == s) {
                online.closeInventory();
            }
        }
    }

    /** Dropper tout le loot à la location (utilisé au break). */
    public void dropLoot(Location loc, Spawner s) {
        if (s == null || loc.getWorld() == null) return;
        for (Map.Entry<Material, Integer> e : s.loot.entrySet()) {
            int remaining = e.getValue();
            while (remaining > 0) {
                int stack = Math.min(remaining, e.getKey().getMaxStackSize());
                loc.getWorld().dropItemNaturally(loc, new ItemStack(e.getKey(), stack));
                remaining -= stack;
            }
        }
        s.loot.clear();
    }

    // =================== ITEM SERIALIZATION ===================

    /** Construit l'ItemStack (Material.SPAWNER) représentant un spawner custom. */
    public ItemStack makeSpawnerItem(SpawnerType type, int stack) {
        ItemStack it = new ItemStack(Material.SPAWNER);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        MiniMessage mm = MiniMessage.miniMessage();
        meta.displayName(mm.deserialize(type.colorTag() + "<bold>Spawner " + type.display() + "</bold>")
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Type: " + type.colorTag() + type.display() + "</gray>")
                .decoration(TextDecoration.ITALIC, false));
        if (stack > 1) {
            lore.add(mm.deserialize("<gray>Stack: <yellow>×" + stack + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(mm.deserialize("<dark_gray>Pose-le. Pose un autre dessus pour stacker.</dark_gray>")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<dark_gray>Clic-droit pour ouvrir le stock.</dark_gray>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, Math.max(1, stack));
        it.setItemMeta(meta);
        it.setAmount(1);
        return it;
    }

    public boolean isSpawnerItem(ItemStack it) {
        if (it == null || it.getType() != Material.SPAWNER) return false;
        if (!it.hasItemMeta()) return false;
        return Byte.valueOf((byte) 1).equals(
                it.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE));
    }

    public SpawnerType readType(ItemStack it) {
        if (!isSpawnerItem(it)) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return SpawnerType.fromId(id);
    }

    public int readStack(ItemStack it) {
        if (!isSpawnerItem(it)) return 0;
        Integer s = it.getItemMeta().getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return s == null ? 1 : s;
    }

    // =================== TICK / LOOT GENERATION ===================

    private void tickAll() {
        for (Spawner s : spawners.values()) {
            World w = Bukkit.getWorld(s.world);
            if (w == null) continue;
            // Chunk non chargé: on skip (ne PAS lire getType qui force-load ou
            // retourne AIR selon Paper, ce qui supprimerait le spawner à tort).
            if (!w.isChunkLoaded(s.x >> 4, s.z >> 4)) continue;
            // Chunk chargé: on peut vérifier que le bloc est toujours un SPAWNER.
            Block b = w.getBlockAt(s.x, s.y, s.z);
            if (b.getType() != Material.SPAWNER) {
                spawners.remove(key(w, s.x, s.y, s.z));
                closeViewers(s);
                deleteFromDb(s);
                continue;
            }

            int rolls = s.stack;
            for (int i = 0; i < rolls; i++) rollLoot(s);
        }
    }

    private void rollLoot(Spawner s) {
        if (totalItems(s) >= MAX_STORAGE_PER_SPAWNER) return;
        SpawnerType type = s.type;
        if (type.loot().isEmpty() || type.totalWeight() <= 0) return;
        int r = random.nextInt(type.totalWeight());
        int acc = 0;
        for (SpawnerType.Drop d : type.loot()) {
            acc += d.weight();
            if (r < acc) {
                int qty = d.min() + (d.max() > d.min() ? random.nextInt(d.max() - d.min() + 1) : 0);
                if (qty <= 0) return;
                s.loot.merge(d.material(), qty, Integer::sum);
                s.markDirty();
                return;
            }
        }
    }

    private int totalItems(Spawner s) {
        int sum = 0;
        for (int v : s.loot.values()) sum += v;
        return sum;
    }

    // =================== DB ===================

    public void loadAll() {
        spawners.clear();
        String selSp = "SELECT id, world, x, y, z, type, stack FROM spawners";
        String selLoot = "SELECT spawner_id, material, amount FROM spawner_loot";
        try (Connection c = db.get()) {
            Map<Long, Spawner> byId = new ConcurrentHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(selSp);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Spawner s = new Spawner();
                    s.dbId = rs.getLong(1);
                    s.world = rs.getString(2);
                    s.x = rs.getInt(3);
                    s.y = rs.getInt(4);
                    s.z = rs.getInt(5);
                    s.type = SpawnerType.fromId(rs.getString(6));
                    s.stack = rs.getInt(7);
                    s.loot = new EnumMap<>(Material.class);
                    if (s.type == null) continue;
                    byId.put(s.dbId, s);
                    // Indexé par nom de monde peu importe qu'il soit chargé —
                    // le tick vérifie `Bukkit.getWorld(...)` avant d'agir.
                    spawners.put(key(s.world, s.x, s.y, s.z), s);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(selLoot);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    Material m = Material.matchMaterial(rs.getString(2));
                    int amount = rs.getInt(3);
                    Spawner s = byId.get(id);
                    if (s != null && m != null && amount > 0) {
                        s.loot.put(m, amount);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load spawners: " + e.getMessage());
        }
        plugin.getLogger().info("Spawners loaded: " + spawners.size());
    }

    private void saveDirty() {
        List<Spawner> dirty = new ArrayList<>();
        for (Spawner s : spawners.values()) if (s.dirty) dirty.add(s);
        if (dirty.isEmpty()) return;
        try (Connection c = db.get()) {
            c.setAutoCommit(false);
            for (Spawner s : dirty) persist(c, s);
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save spawners: " + e.getMessage());
        }
    }

    /** Persistance synchrone totale (appelée au stop). */
    public void saveAll() {
        try (Connection c = db.get()) {
            c.setAutoCommit(false);
            for (Spawner s : spawners.values()) persist(c, s);
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to saveAll spawners: " + e.getMessage());
        }
    }

    private void persist(Connection c, Spawner s) throws SQLException {
        if (s.dbId <= 0) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO spawners(world,x,y,z,type,stack) VALUES(?,?,?,?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, s.world);
                ps.setInt(2, s.x);
                ps.setInt(3, s.y);
                ps.setInt(4, s.z);
                ps.setString(5, s.type.name());
                ps.setInt(6, s.stack);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) s.dbId = keys.getLong(1);
                }
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE spawners SET type=?, stack=? WHERE id=?")) {
                ps.setString(1, s.type.name());
                ps.setInt(2, s.stack);
                ps.setLong(3, s.dbId);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM spawner_loot WHERE spawner_id=?")) {
            ps.setLong(1, s.dbId);
            ps.executeUpdate();
        }
        if (!s.loot.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO spawner_loot(spawner_id, material, amount) VALUES(?,?,?)")) {
                for (Map.Entry<Material, Integer> e : s.loot.entrySet()) {
                    if (e.getValue() == null || e.getValue() <= 0) continue;
                    ps.setLong(1, s.dbId);
                    ps.setString(2, e.getKey().name());
                    ps.setInt(3, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        s.dirty = false;
    }

    private void deleteFromDb(Spawner s) {
        if (s == null || s.dbId <= 0) return;
        try (Connection c = db.get()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM spawner_loot WHERE spawner_id=?")) {
                ps.setLong(1, s.dbId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM spawners WHERE id=?")) {
                ps.setLong(1, s.dbId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete spawner: " + e.getMessage());
        }
    }

    // =================== DATA ===================

    /** Etat runtime d'un spawner. */
    public static class Spawner {
        public long dbId;
        public String world;
        public int x, y, z;
        public SpawnerType type;
        public int stack;
        public Map<Material, Integer> loot = new EnumMap<>(Material.class);
        public volatile boolean dirty = false;

        public void markDirty() { dirty = true; }

        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }

        /** Liste triée par quantité décroissante des items présents. */
        public List<Map.Entry<Material, Integer>> snapshot() {
            List<Map.Entry<Material, Integer>> out = new ArrayList<>();
            for (Map.Entry<Material, Integer> e : loot.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    out.add(Map.entry(e.getKey(), e.getValue()));
                }
            }
            out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            return Collections.unmodifiableList(out);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Spawner s)) return false;
            return Objects.equals(world, s.world) && x == s.x && y == s.y && z == s.z;
        }

        @Override
        public int hashCode() { return Objects.hash(world, x, y, z); }
    }
}
