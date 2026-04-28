package fr.smp.core.enchants;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves all "area mining" / special-break custom enchants in a single
 * BlockBreakEvent handler. Recursive calls are guarded by a ThreadLocal flag
 * so one player swing only triggers our logic on the outer-most break.
 */
public class EnchantBreakListener implements Listener {

    private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> false);
    private static final int VEIN_LIMIT = 128;
    private static final int TREE_LIMIT = 1024;

    private final SMPCore plugin;

    public EnchantBreakListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Core dispatch
    // ════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (REENTRY.get()) return;

        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        Block origin = e.getBlock();

        int vein = EnchantEngine.levelOf(tool, CustomEnchant.VEIN);
        int feller = EnchantEngine.levelOf(tool, CustomEnchant.FELLER);
        int quarry = EnchantEngine.levelOf(tool, CustomEnchant.QUARRY);
        int excav  = EnchantEngine.levelOf(tool, CustomEnchant.EXCAV);
        int harvest= EnchantEngine.levelOf(tool, CustomEnchant.HARVEST);
        int replant= EnchantEngine.levelOf(tool, CustomEnchant.REPLANT);

        // Replant the origin block too (before vanilla break drops it).
        if (replant > 0) {
            plugin.getLogger().info("[replant] onBreak: tool has replant=" + replant +
                    ", block=" + origin.getType() + ", mature=" + isMatureCrop(origin));
            if (isMatureCrop(origin)) {
                scheduleReplant(p, origin.getLocation(), origin.getType());
            }
        }

        if (vein > 0 && isOre(origin.getType())) {
            handleVeinMiner(p, origin, tool);
            return;
        }
        if (feller > 0 && isLog(origin.getType())) {
            handleTreeFeller(p, origin, tool);
            return;
        }
        if (quarry > 0 && isQuarryBlock(origin.getType())) {
            handleAreaBreak(p, origin, tool, EnchantEngine.bigSide(quarry), StoneLike.INSTANCE);
            return;
        }
        if (excav > 0 && isSoftGround(origin.getType())) {
            handleAreaBreak(p, origin, tool, EnchantEngine.bigSide(excav), SoftGround.INSTANCE);
            return;
        }
        if (harvest > 0 && isCrop(origin.getType())) {
            handleAreaBreak(p, origin, tool, EnchantEngine.bigSide(harvest), Crop.INSTANCE);
        }
    }

    /** Magnet + AutoSmelt: rewrite drops before they hit the ground. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        boolean smelt = EnchantEngine.levelOf(tool, CustomEnchant.SMELT) > 0;
        boolean magnet= EnchantEngine.levelOf(tool, CustomEnchant.MAGNET) > 0;
        boolean voidstone = plugin.voidstones() != null && plugin.voidstones().hasVoidstone(p.getInventory());
        if (!smelt && !magnet && !voidstone) return;

        List<Item> items = e.getItems();
        if (items == null || items.isEmpty()) return;

        if (smelt) {
            for (Item it : items) {
                ItemStack s = it.getItemStack();
                Material cooked = smeltResult(s.getType());
                if (cooked != null) {
                    it.setItemStack(new ItemStack(cooked, s.getAmount()));
                }
            }
        }
        if (voidstone) {
            plugin.voidstones().absorbDrops(p.getInventory(), items);
        }
        if (magnet) {
            for (var it = items.listIterator(); it.hasNext(); ) {
                Item drop = it.next();
                if (drop == null) {
                    it.remove();
                    continue;
                }

                ItemStack stack = drop.getItemStack();
                if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                    it.remove();
                    continue;
                }

                ItemStack toInsert = stack.clone();
                var leftover = p.getInventory().addItem(toInsert);
                if (leftover.isEmpty()) {
                    it.remove();
                } else {
                    drop.setItemStack(leftover.values().iterator().next());
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  VeinMiner
    // ════════════════════════════════════════════════════════════════════

    private void handleVeinMiner(Player p, Block origin, ItemStack tool) {
        Material type = origin.getType();
        Set<Location> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin.getLocation());
        int broken = 0;

        REENTRY.set(true);
        try {
            while (!queue.isEmpty() && broken < VEIN_LIMIT) {
                Block b = queue.pop();
                if (b != origin) {
                    if (b.getType() != type) continue;
                    if (!tryBreak(p, b, tool)) continue;
                }
                broken++;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block n = b.getRelative(dx, dy, dz);
                    Location key = n.getLocation();
                    if (visited.contains(key)) continue;
                    if (n.getType() != type) continue;
                    visited.add(key);
                    queue.add(n);
                }
            }
        } finally {
            REENTRY.set(false);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  TreeFeller
    // ════════════════════════════════════════════════════════════════════

    private void handleTreeFeller(Player p, Block origin, ItemStack tool) {
        Material logType = origin.getType();
        Set<Location> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin.getLocation());
        int broken = 0;

        REENTRY.set(true);
        try {
            while (!queue.isEmpty() && broken < TREE_LIMIT) {
                Block b = queue.pop();
                boolean isLogHere = isLog(b.getType()) && matchesLogFamily(b.getType(), logType);
                boolean isLeafHere = isLeaf(b.getType());
                if (b != origin) {
                    if (!isLogHere && !isLeafHere) continue;
                    if (!tryBreak(p, b, tool)) continue;
                }
                broken++;
                if (!isLogHere && !isLeafHere && b != origin) continue;

                // Expand only through logs; let leaves break naturally afterward.
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block n = b.getRelative(dx, dy, dz);
                    Location key = n.getLocation();
                    if (visited.contains(key)) continue;
                    Material nm = n.getType();
                    if (matchesLogFamily(nm, logType) || isLeaf(nm)) {
                        visited.add(key);
                        queue.add(n);
                    }
                }
            }
        } finally {
            REENTRY.set(false);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Area break (Quarry / Excavator / Harvester)
    // ════════════════════════════════════════════════════════════════════

    private interface AreaFilter { boolean breakable(Material m); boolean threeD(); }

    private static final class StoneLike implements AreaFilter {
        static final StoneLike INSTANCE = new StoneLike();
        public boolean breakable(Material m) { return isQuarryBlock(m); }
        public boolean threeD() { return true; }
    }
    private static final class SoftGround implements AreaFilter {
        static final SoftGround INSTANCE = new SoftGround();
        public boolean breakable(Material m) { return isSoftGround(m); }
        public boolean threeD() { return true; }
    }
    private static final class Crop implements AreaFilter {
        static final Crop INSTANCE = new Crop();
        public boolean breakable(Material m) { return isCrop(m); }
        public boolean threeD() { return false; }
    }

    private void handleAreaBreak(Player p, Block origin, ItemStack tool, int side, AreaFilter filter) {
        BlockFace face = bestFace(p);

        // For a square of side S centered on origin, the offset ranges are:
        //   S=3 → [-1..1]
        //   S=4 → [-1..2]  (asymmetric, matches user spec)
        //   S=5 → [-2..2]
        int neg, pos;
        switch (side) {
            case 3 -> { neg = -1; pos = 1; }
            case 4 -> { neg = -1; pos = 2; }
            case 5 -> { neg = -2; pos = 2; }
            default -> { neg = -1; pos = 1; }
        }

        REENTRY.set(true);
        try {
            for (int a = neg; a <= pos; a++) {
                for (int b = neg; b <= pos; b++) {
                    Block bl;
                    if (face == BlockFace.UP || face == BlockFace.DOWN) {
                        bl = origin.getRelative(a, 0, b);
                    } else if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                        bl = origin.getRelative(a, b, 0);
                    } else { // EAST/WEST
                        bl = origin.getRelative(0, b, a);
                    }
                    if (bl.getLocation().equals(origin.getLocation())) continue;
                    if (!filter.breakable(bl.getType())) continue;
                    tryBreak(p, bl, tool);
                }
            }
        } finally {
            REENTRY.set(false);
        }
    }

    /** Determine which face the player is looking at. */
    private BlockFace bestFace(Player p) {
        Vector dir = p.getEyeLocation().getDirection();
        double ax = Math.abs(dir.getX()), ay = Math.abs(dir.getY()), az = Math.abs(dir.getZ());
        if (ay >= ax && ay >= az) return dir.getY() > 0 ? BlockFace.UP : BlockFace.DOWN;
        if (ax >= az) return dir.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        return dir.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    /**
     * Break a block as if the player swung on it: fires BlockBreakEvent (so
     * claim/anti-grief plugins get their shot), handles tool durability +
     * Unbreaking, drops items AND drops the XP orb — which the older
     * breakNaturally path was silently skipping, making Quarry/Excavator/
     * VeinMiner eat all the experience from the extra blocks.
     * The REENTRY guard in {@link #onBreak} keeps this from recursing.
     */
    private boolean tryBreak(Player p, Block b, ItemStack tool) {
        if (b.getType().isAir()) return false;
        if (b.getType().getHardness() < 0) return false; // bedrock-like

        boolean replantCrop = isMatureCrop(b)
                && EnchantEngine.levelOf(tool, CustomEnchant.REPLANT) > 0;
        Material cropType = replantCrop ? b.getType() : null;
        Location cropLoc  = replantCrop ? b.getLocation() : null;

        boolean broken = p.breakBlock(b);

        if (broken && replantCrop) {
            scheduleReplant(p, cropLoc, cropType);
        }
        return broken;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Replant (Replantage)
    // ════════════════════════════════════════════════════════════════════

    private static boolean isMatureCrop(Block b) {
        if (!isCrop(b.getType())) return false;
        if (b.getBlockData() instanceof Ageable a) {
            return a.getAge() >= a.getMaximumAge();
        }
        return false;
    }

    private static Material cropSeed(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    /** Regrow the crop next tick — the seed comes from the harvest itself. */
    private void scheduleReplant(Player p, Location loc, Material cropType) {
        if (cropSeed(cropType) == null) {
            plugin.getLogger().info("[replant] skipped: no seed mapping for " + cropType);
            return;
        }
        plugin.getLogger().info("[replant] scheduling " + cropType + " at " +
                loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());

        Bukkit.getScheduler().runTask(plugin, () -> {
            Block here = loc.getBlock();
            Material here0 = here.getType();
            if (!here0.isAir()) {
                plugin.getLogger().info("[replant] abort: block not air, got " + here0);
                return;
            }
            Block below = here.getRelative(BlockFace.DOWN);
            Material required = (cropType == Material.NETHER_WART) ? Material.SOUL_SAND : Material.FARMLAND;
            if (below.getType() != required) {
                plugin.getLogger().info("[replant] abort: below is " + below.getType() + ", need " + required);
                return;
            }
            here.setType(cropType, false);
            plugin.getLogger().info("[replant] ok: " + cropType + " replanted");
        });
    }

    private static boolean isOre(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS || m == Material.RAW_IRON_BLOCK
                || m == Material.RAW_COPPER_BLOCK || m == Material.RAW_GOLD_BLOCK;
    }

    private static boolean isLog(Material m) {
        return Tag.LOGS.isTagged(m);
    }

    private static boolean isLeaf(Material m) {
        return Tag.LEAVES.isTagged(m);
    }

    /** Same wood family (oak log ≠ cherry log → false). */
    private static boolean matchesLogFamily(Material a, Material b) {
        if (a == b) return true;
        if (!isLog(a) || !isLog(b)) return false;
        // Group stripped/non-stripped together
        String na = a.name().replace("STRIPPED_", "").replace("_WOOD", "_LOG");
        String nb = b.name().replace("STRIPPED_", "").replace("_WOOD", "_LOG");
        return na.equals(nb);
    }

    private static final Set<Material> STONE_EXTRA = EnumSet.of(
            Material.NETHERRACK, Material.END_STONE, Material.OBSIDIAN,
            Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE,
            Material.GILDED_BLACKSTONE, Material.CALCITE, Material.TUFF,
            Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.COBBLED_DEEPSLATE,
            Material.SANDSTONE, Material.CHISELED_SANDSTONE, Material.CUT_SANDSTONE, Material.SMOOTH_SANDSTONE,
            Material.RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE, Material.CUT_RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE,
            Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA,
            Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA,
            Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.BLACK_TERRACOTTA,
            Material.DRIPSTONE_BLOCK, Material.POINTED_DRIPSTONE,
            Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            Material.MAGMA_BLOCK, Material.PACKED_MUD);

    private static boolean isStoneLike(Material m) {
        if (STONE_EXTRA.contains(m)) return true;
        if (m.name().endsWith("_ORE")) return true;
        return Tag.BASE_STONE_OVERWORLD.isTagged(m);
    }

    /** Quarry breaks stone-like blocks AND the "filler" ground found in mines (dirt, sand, gravel, grass…). */
    private static boolean isQuarryBlock(Material m) {
        return isStoneLike(m) || isSoftGround(m);
    }

    private static final Set<Material> SOFT_GROUND = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM,
            Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MUD, Material.CLAY,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.DIRT_PATH,
            Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW_BLOCK);

    private static boolean isSoftGround(Material m) {
        return SOFT_GROUND.contains(m);
    }

    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART);

    private static boolean isCrop(Material m) {
        return CROPS.contains(m);
    }

    /** Map an ore/food material to its smelted form; null = no smelting. */
    private static Material smeltResult(Material m) {
        return switch (m) {
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER -> Material.COPPER_INGOT;
            case COBBLESTONE, COBBLED_DEEPSLATE -> Material.STONE;
            case STONE -> Material.SMOOTH_STONE;
            case SAND, RED_SAND -> Material.GLASS;
            case CLAY_BALL -> Material.BRICK;
            case NETHERRACK -> Material.NETHER_BRICK;
            case KELP -> Material.DRIED_KELP;
            case PORKCHOP -> Material.COOKED_PORKCHOP;
            case BEEF -> Material.COOKED_BEEF;
            case CHICKEN -> Material.COOKED_CHICKEN;
            case MUTTON -> Material.COOKED_MUTTON;
            case RABBIT -> Material.COOKED_RABBIT;
            case SALMON -> Material.COOKED_SALMON;
            case COD -> Material.COOKED_COD;
            default -> null;
        };
    }
}
