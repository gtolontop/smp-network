package fr.smp.logger.scan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.bukkit.Material;

import java.util.Map;
import java.util.Set;

public final class ItemNbtWalker {

    private ItemNbtWalker() {}

    public static String nbtStr(CompoundTag tag, String key) {
        return tag.getString(key).orElse("");
    }

    public static int nbtInt(CompoundTag tag, String key) {
        return tag.getInt(key).orElse(0);
    }

    public static CompoundTag nbtCompound(CompoundTag tag, String key) {
        return tag.getCompound(key).orElse(null);
    }

    public static CompoundTag nbtCompound(ListTag list, int index) {
        return list.getCompound(index).orElse(null);
    }

    public static ListTag nbtList(CompoundTag tag, String key) {
        return tag.getList(key).orElse(null);
    }

    public static int nbtCount(CompoundTag item) {
        int c = nbtInt(item, "count");
        if (c > 0) return c;
        c = item.getByte("Count").orElse((byte) 0) & 0xFF;
        if (c > 0) return c;
        return 1;
    }

    public static int countMatch(ListTag list, String targetId) {
        if (list == null) return 0;
        int total = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = nbtCompound(list, i);
            if (item == null) continue;
            if (targetId.equals(nbtStr(item, "id"))) total += nbtCount(item);
            total += scanShulkerCount(item, targetId);
        }
        return total;
    }

    public static void tallyMatch(ListTag list, Set<String> targetIds,
                                  Map<String, Material> idToMat,
                                  Map<Material, Integer> totals) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = nbtCompound(list, i);
            if (item == null) continue;
            String id = nbtStr(item, "id");
            if (targetIds.contains(id)) {
                Material m = idToMat.get(id);
                if (m != null) totals.merge(m, nbtCount(item), Integer::sum);
            }
            scanShulkerTally(item, targetIds, idToMat, totals);
        }
    }

    private static int scanShulkerCount(CompoundTag item, String targetId) {
        String id = nbtStr(item, "id");
        if (!id.contains("shulker_box")) return 0;
        int total = 0;
        CompoundTag comps = nbtCompound(item, "components");
        if (comps != null) {
            ListTag container = nbtList(comps, "minecraft:container");
            if (container != null) {
                for (int i = 0; i < container.size(); i++) {
                    CompoundTag entry = nbtCompound(container, i);
                    if (entry == null) continue;
                    CompoundTag inner = nbtCompound(entry, "item");
                    if (inner != null && targetId.equals(nbtStr(inner, "id"))) {
                        total += nbtCount(inner);
                    }
                }
            }
        }
        CompoundTag tag = nbtCompound(item, "tag");
        if (tag != null) {
            CompoundTag bet = nbtCompound(tag, "BlockEntityTag");
            if (bet != null) total += countMatch(nbtList(bet, "Items"), targetId);
        }
        return total;
    }

    private static void scanShulkerTally(CompoundTag item, Set<String> targetIds,
                                         Map<String, Material> idToMat,
                                         Map<Material, Integer> totals) {
        String id = nbtStr(item, "id");
        if (!id.contains("shulker_box")) return;
        CompoundTag comps = nbtCompound(item, "components");
        if (comps != null) {
            ListTag container = nbtList(comps, "minecraft:container");
            if (container != null) {
                for (int i = 0; i < container.size(); i++) {
                    CompoundTag entry = nbtCompound(container, i);
                    if (entry == null) continue;
                    CompoundTag inner = nbtCompound(entry, "item");
                    if (inner != null) {
                        String innerId = nbtStr(inner, "id");
                        if (targetIds.contains(innerId)) {
                            Material m = idToMat.get(innerId);
                            if (m != null) totals.merge(m, nbtCount(inner), Integer::sum);
                        }
                    }
                }
            }
        }
        CompoundTag tag = nbtCompound(item, "tag");
        if (tag != null) {
            CompoundTag bet = nbtCompound(tag, "BlockEntityTag");
            if (bet != null) tallyMatch(nbtList(bet, "Items"), targetIds, idToMat, totals);
        }
    }
}
