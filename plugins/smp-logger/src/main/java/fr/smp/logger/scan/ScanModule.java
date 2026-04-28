package fr.smp.logger.scan;

import fr.smp.logger.SMPLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ScanModule {

    private final SMPLogger plugin;
    private final RegionScanner regionScanner = new RegionScanner();

    public ScanModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    public RegionScanner regionScanner() {
        return regionScanner;
    }

    public record PlayerBreakdown(String name, int inv, int ender, boolean online) {
        public int total() { return inv + ender; }
    }

    public record SingleResult(Material material, int total, int playerCount, List<PlayerBreakdown> top) {}

    public Map<UUID, int[]> snapshotOnline(Material material) {
        Map<UUID, int[]> map = new LinkedHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            int inv = countBukkitInventory(p, material);
            int ender = countBukkitEnder(p, material);
            if (inv > 0 || ender > 0) map.put(p.getUniqueId(), new int[]{inv, ender});
        }
        return map;
    }

    public Map<Material, Integer> snapshotOnlineSummary(Set<Material> targets) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (Material m : targets) totals.put(m, 0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Material m : targets) {
                totals.merge(m, countBukkitInventory(p, m) + countBukkitEnder(p, m), Integer::sum);
            }
        }
        return totals;
    }

    public SingleResult scan(Material material, Map<UUID, int[]> onlineCounts) {
        File dir = playerDataDir();
        if (dir == null || !dir.exists()) return buildResult(material, onlineCounts, List.of());

        String targetId = material.getKey().toString();
        Set<UUID> onlineUuids = onlineCounts.keySet();
        Map<UUID, int[]> counts = new LinkedHashMap<>(onlineCounts);

        File[] files = dir.listFiles((d, n) -> n.endsWith(".dat"));
        if (files != null) {
            for (File f : files) {
                try {
                    UUID uuid = UUID.fromString(f.getName().replace(".dat", ""));
                    if (onlineUuids.contains(uuid)) continue;
                    int[] c = countInFile(f.toPath(), targetId);
                    if (c[0] > 0 || c[1] > 0) counts.put(uuid, c);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return buildResult(material, counts, new ArrayList<>(counts.keySet()));
    }

    public Map<Material, Integer> scanSummary(Set<Material> targets, Map<Material, Integer> onlineTotals, Set<UUID> onlineUuids) {
        File dir = playerDataDir();
        Map<Material, Integer> totals = new LinkedHashMap<>(onlineTotals);
        if (dir == null || !dir.exists()) return totals;

        Set<String> targetIds = new HashSet<>();
        Map<String, Material> idToMat = new HashMap<>();
        for (Material m : targets) {
            String id = m.getKey().toString();
            targetIds.add(id);
            idToMat.put(id, m);
        }

        File[] files = dir.listFiles((d, n) -> n.endsWith(".dat"));
        if (files != null) {
            for (File f : files) {
                try {
                    UUID uuid = UUID.fromString(f.getName().replace(".dat", ""));
                    if (onlineUuids.contains(uuid)) continue;
                    tallyFile(f.toPath(), targetIds, idToMat, totals);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return totals;
    }

    public int countTotalPlayers() {
        File dir = playerDataDir();
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".dat"));
        return files == null ? 0 : files.length;
    }

    public Set<Material> parseRareItems() {
        Set<Material> out = new LinkedHashSet<>();
        for (String s : plugin.getConfig().getStringList("rare.items")) {
            try { out.add(Material.valueOf(s)); } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    private SingleResult buildResult(Material material, Map<UUID, int[]> counts, List<UUID> uuids) {
        List<PlayerBreakdown> list = new ArrayList<>();
        int total = 0;
        for (UUID uuid : uuids) {
            int[] c = counts.get(uuid);
            if (c == null) continue;
            total += c[0] + c[1];
            list.add(new PlayerBreakdown(resolveName(uuid), c[0], c[1], Bukkit.getPlayer(uuid) != null));
        }
        list.sort(Comparator.comparingInt(PlayerBreakdown::total).reversed());
        return new SingleResult(material, total, counts.size(), list);
    }

    // --- NBT helpers (async-safe, player .dat files) ---

    private int[] countInFile(Path path, String targetId) {
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            int inv = ItemNbtWalker.countMatch(ItemNbtWalker.nbtList(root, "Inventory"), targetId);
            int ender = ItemNbtWalker.countMatch(ItemNbtWalker.nbtList(root, "EnderChestItems"), targetId);
            return new int[]{inv, ender};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private void tallyFile(Path path, Set<String> targetIds, Map<String, Material> idToMat, Map<Material, Integer> totals) {
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            ItemNbtWalker.tallyMatch(ItemNbtWalker.nbtList(root, "Inventory"), targetIds, idToMat, totals);
            ItemNbtWalker.tallyMatch(ItemNbtWalker.nbtList(root, "EnderChestItems"), targetIds, idToMat, totals);
        } catch (Exception ignored) {}
    }

    // --- Bukkit API helpers (main thread only) ---

    private int countBukkitInventory(Player p, Material mat) {
        int total = 0;
        total += countBukkitItems(p.getInventory().getContents(), mat);
        total += countBukkitItems(p.getInventory().getArmorContents(), mat);
        total += countBukkitItems(p.getInventory().getExtraContents(), mat);
        return total;
    }

    private int countBukkitEnder(Player p, Material mat) {
        return countBukkitItems(p.getEnderChest().getContents(), mat);
    }

    private int countBukkitItems(ItemStack[] items, Material mat) {
        int total = 0;
        for (ItemStack it : items) total += countBukkitItem(it, mat);
        return total;
    }

    private int countBukkitItem(ItemStack it, Material mat) {
        if (it == null || it.getType().isAir()) return 0;
        int total = 0;
        if (it.getType() == mat) total += it.getAmount();
        if (it.getType().name().contains("SHULKER_BOX") && it.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox shulker) {
            for (ItemStack inner : shulker.getInventory().getContents()) {
                if (inner != null && inner.getType() == mat) total += inner.getAmount();
            }
        }
        return total;
    }

    // --- Utility ---

    private File playerDataDir() {
        var worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) return null;
        return new File(worlds.get(0).getWorldFolder(), "playerdata");
    }

    private String resolveName(UUID uuid) {
        var entry = plugin.players().byUuid(uuid);
        if (entry != null) return entry.name();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString().substring(0, 8) + "...";
    }
}
