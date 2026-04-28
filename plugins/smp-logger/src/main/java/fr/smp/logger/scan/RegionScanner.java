package fr.smp.logger.scan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads Anvil region (.mca) files directly off-thread and walks block_entities
 * to count items in containers placed anywhere in the world — even in unloaded
 * chunks. Pure I/O + NBT parsing; never touches Bukkit chunk data so it cannot
 * lag the main thread or load chunks.
 */
public final class RegionScanner {

    public interface Progress {
        void update(int regionsDone, int regionsTotal, int containers, long items);
    }

    private static final java.util.Set<String> SKIP_DIRS = java.util.Set.of(
            "playerdata", "datapacks", "logs", "stats", "advancements",
            "data", "poi", "entities", "raids", "level.dat_old"
    );

    /** Walks all worlds and gathers every region/*.mca file (overworld, nether, end, datapack dimensions). */
    public List<File> listAllRegionFiles() {
        List<File> all = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            collect(w.getWorldFolder(), all);
        }
        return all;
    }

    private static void collect(File dir, List<File> out) {
        if (dir == null) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (!c.isDirectory()) continue;
            String name = c.getName();
            if (name.equals("region")) {
                File[] mcas = c.listFiles((d, n) -> n.endsWith(".mca"));
                if (mcas != null) Collections.addAll(out, mcas);
            } else if (!SKIP_DIRS.contains(name)) {
                collect(c, out);
            }
        }
    }

    public record SummaryResult(Map<Material, Integer> totals, int regionsScanned,
                                int containersFound, long elapsedMs) {}

    public record SingleResult(Material material, int total, int containersFound,
                               int regionsScanned, long elapsedMs) {}

    public SummaryResult scanSummary(Set<Material> materials, Progress progress) {
        long t0 = System.currentTimeMillis();
        Map<String, Material> idToMat = new java.util.HashMap<>();
        java.util.Set<String> targetIds = new java.util.HashSet<>();
        for (Material m : materials) {
            String id = m.getKey().toString();
            idToMat.put(id, m);
            targetIds.add(id);
        }
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (Material m : materials) totals.put(m, 0);

        List<File> regions = listAllRegionFiles();
        int total = regions.size();
        AtomicInteger containers = new AtomicInteger();
        int done = 0;
        for (File mca : regions) {
            int c = walkRegion(mca, targetIds, idToMat, totals, null, 0L, null);
            containers.addAndGet(c);
            done++;
            if (progress != null && (done % 16 == 0 || done == total)) {
                long sum = 0;
                for (int v : totals.values()) sum += v;
                progress.update(done, total, containers.get(), sum);
            }
        }
        return new SummaryResult(totals, total, containers.get(), System.currentTimeMillis() - t0);
    }

    public SingleResult scanSingle(Material material, Progress progress) {
        long t0 = System.currentTimeMillis();
        String targetId = material.getKey().toString();

        List<File> regions = listAllRegionFiles();
        int total = regions.size();
        long[] sum = {0L};
        int[] containers = {0};
        int done = 0;
        for (File mca : regions) {
            int c = walkRegion(mca, null, null, null, targetId, 0L, sum);
            containers[0] += c;
            done++;
            if (progress != null && (done % 16 == 0 || done == total)) {
                progress.update(done, total, containers[0], sum[0]);
            }
        }
        return new SingleResult(material, (int) Math.min(Integer.MAX_VALUE, sum[0]),
                containers[0], total, System.currentTimeMillis() - t0);
    }

    /**
     * If targetIds!=null → tally mode (multi-material into totals map).
     * If targetId!=null  → count mode (single material into singleSum[0]).
     * Returns containers visited (with non-empty Items list).
     */
    private int walkRegion(File mca, Set<String> targetIds, Map<String, Material> idToMat,
                           Map<Material, Integer> totals,
                           String targetId, long ignored, long[] singleSum) {
        int containers = 0;
        try (RandomAccessFile raf = new RandomAccessFile(mca, "r")) {
            long fileLen = raf.length();
            if (fileLen < 8192) return 0;
            byte[] header = new byte[4096];
            raf.readFully(header);

            int regionX = 0, regionZ = 0;
            String fname = mca.getName();
            try {
                String[] parts = fname.split("\\.");
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            } catch (Exception ignore) {}

            for (int i = 0; i < 1024; i++) {
                int b0 = header[i * 4] & 0xFF;
                int b1 = header[i * 4 + 1] & 0xFF;
                int b2 = header[i * 4 + 2] & 0xFF;
                int sCount = header[i * 4 + 3] & 0xFF;
                int sOff = (b0 << 16) | (b1 << 8) | b2;
                if (sOff == 0 || sCount == 0) continue;
                long pos = (long) sOff * 4096L;
                if (pos + 5 > fileLen) continue;
                raf.seek(pos);
                int chunkLen;
                int compType;
                try {
                    chunkLen = raf.readInt();
                    compType = raf.readByte() & 0xFF;
                } catch (IOException e) {
                    continue;
                }
                if (chunkLen <= 0) continue;
                int dataLen = chunkLen - 1;
                boolean external = (compType & 0x80) != 0;
                int realComp = compType & 0x7F;
                CompoundTag chunk;
                if (external) {
                    int chunkX = regionX * 32 + (i % 32);
                    int chunkZ = regionZ * 32 + (i / 32);
                    chunk = readExternal(mca.getParentFile(), chunkX, chunkZ, realComp);
                } else {
                    if (pos + 5 + dataLen > fileLen) continue;
                    byte[] data;
                    try {
                        data = new byte[dataLen];
                        raf.readFully(data);
                    } catch (Exception e) {
                        continue;
                    }
                    chunk = decodeChunk(data, realComp);
                }
                if (chunk == null) continue;
                ListTag bes = chunk.getList("block_entities").orElse(null);
                if (bes == null || bes.isEmpty()) continue;
                for (int j = 0; j < bes.size(); j++) {
                    CompoundTag be = bes.getCompound(j).orElse(null);
                    if (be == null) continue;
                    ListTag items = be.getList("Items").orElse(null);
                    if (items == null || items.isEmpty()) continue;
                    containers++;
                    if (targetIds != null) {
                        ItemNbtWalker.tallyMatch(items, targetIds, idToMat, totals);
                    } else if (targetId != null) {
                        singleSum[0] += ItemNbtWalker.countMatch(items, targetId);
                    }
                }
            }
        } catch (IOException e) {
            // skip corrupt or locked region
        }
        return containers;
    }

    private CompoundTag decodeChunk(byte[] data, int compType) {
        try {
            InputStream raw;
            switch (compType) {
                case 1: raw = new GZIPInputStream(new ByteArrayInputStream(data)); break;
                case 2: raw = new InflaterInputStream(new ByteArrayInputStream(data)); break;
                case 3: raw = new ByteArrayInputStream(data); break;
                default: return null; // LZ4 (4) and others — Vanilla server uses zlib by default
            }
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(raw))) {
                return NbtIo.read(dis, NbtAccounter.unlimitedHeap());
            }
        } catch (IOException e) {
            return null;
        }
    }

    private CompoundTag readExternal(File regionDir, int chunkX, int chunkZ, int compType) {
        File ext = new File(regionDir, "c." + chunkX + "." + chunkZ + ".mcc");
        if (!ext.exists()) return null;
        try {
            byte[] data = Files.readAllBytes(ext.toPath());
            return decodeChunk(data, compType);
        } catch (IOException e) {
            return null;
        }
    }
}
