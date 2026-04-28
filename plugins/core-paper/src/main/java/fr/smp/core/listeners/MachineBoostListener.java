package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MachineBoostListener implements Listener {

    private static final int MAX_CRAFTER_CRAFTS_PER_PULSE = 64;
    private static final double MAX_FURNACE_MULTIPLIER = 200.0;

    private final SMPCore plugin;
    private final boolean enabled;
    private final boolean craftersEnabled;
    private final int crafterCraftsPerPulse;
    private final long crafterTicksBetweenCrafts;
    private final boolean crafterRequirePower;
    private final boolean dispensersEnabled;
    private final int dispenserActionsPerPulse;
    private final long dispenserTicksBetweenActions;
    private final boolean dispenserRequirePower;
    private final boolean furnacesEnabled;
    private final double furnaceCookSpeedMultiplier;
    private final double furnaceFuelTimeMultiplier;
    private final Map<BlockKey, Integer> crafterBursts = new HashMap<>();
    private final Map<BlockKey, Integer> dispenserBursts = new HashMap<>();

    public MachineBoostListener(SMPCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("machines.enabled", true);
        this.craftersEnabled = plugin.getConfig().getBoolean("machines.crafters.enabled", true);
        this.crafterCraftsPerPulse = clamp(
                plugin.getConfig().getInt("machines.crafters.crafts-per-pulse", 16),
                1,
                MAX_CRAFTER_CRAFTS_PER_PULSE
        );
        this.crafterTicksBetweenCrafts = Math.max(1L,
                plugin.getConfig().getLong("machines.crafters.ticks-between-crafts", 1L));
        this.crafterRequirePower = plugin.getConfig().getBoolean("machines.crafters.require-power", false);
        this.dispensersEnabled = plugin.getConfig().getBoolean("machines.dispensers.enabled", true);
        this.dispenserActionsPerPulse = clamp(
                plugin.getConfig().getInt("machines.dispensers.actions-per-pulse", 16),
                1,
                MAX_CRAFTER_CRAFTS_PER_PULSE
        );
        this.dispenserTicksBetweenActions = Math.max(1L,
                plugin.getConfig().getLong("machines.dispensers.ticks-between-actions", 1L));
        this.dispenserRequirePower = plugin.getConfig().getBoolean("machines.dispensers.require-power", false);
        this.furnacesEnabled = plugin.getConfig().getBoolean("machines.furnaces.enabled", true);
        this.furnaceCookSpeedMultiplier = clamp(
                plugin.getConfig().getDouble("machines.furnaces.cook-speed-multiplier", MAX_FURNACE_MULTIPLIER),
                1.0,
                MAX_FURNACE_MULTIPLIER
        );
        this.furnaceFuelTimeMultiplier = clamp(
                plugin.getConfig().getDouble("machines.furnaces.fuel-time-multiplier", 64.0),
                1.0,
                MAX_FURNACE_MULTIPLIER
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (!enabled || !craftersEnabled) return;
        Block block = event.getBlock();
        if (block.getType() != Material.CRAFTER) return;

        BlockKey key = BlockKey.from(block);
        Integer remaining = crafterBursts.get(key);
        if (remaining != null) {
            int next = remaining - 1;
            if (next > 0) {
                crafterBursts.put(key, next);
                scheduleCrafterTick(block, key);
            } else {
                crafterBursts.remove(key);
            }
            return;
        }

        int extraCrafts = crafterCraftsPerPulse - 1;
        if (extraCrafts <= 0) return;

        crafterBursts.put(key, extraCrafts);
        scheduleCrafterTick(block, key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (!enabled || !dispensersEnabled) return;
        Block block = event.getBlock();
        if (block.getType() != Material.DISPENSER && block.getType() != Material.DROPPER) return;

        BlockKey key = BlockKey.from(block);
        Integer remaining = dispenserBursts.get(key);
        if (remaining != null) {
            int next = remaining - 1;
            if (next > 0) {
                dispenserBursts.put(key, next);
                scheduleDispenserTick(block, key);
            } else {
                dispenserBursts.remove(key);
            }
            return;
        }

        int extraActions = dispenserActionsPerPulse - 1;
        if (extraActions <= 0) return;

        dispenserBursts.put(key, extraActions);
        scheduleDispenserTick(block, key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        if (!enabled || !furnacesEnabled) return;
        applyFurnaceBoost(event.getBlock());
        event.setTotalCookTime(boostedCookTime(event.getTotalCookTime()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (!enabled || !furnacesEnabled) return;
        applyFurnaceBoost(event.getBlock());
        event.setBurnTime(boostedBurnTime(event.getBurnTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled || !furnacesEnabled) return;
        applyFurnaceBoost(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!enabled || !furnacesEnabled) return;
        if (event.getInventory().getLocation() == null) return;
        applyFurnaceBoost(event.getInventory().getLocation().getBlock());
    }

    private void scheduleCrafterTick(Block source, BlockKey key) {
        scheduleMachineTick(source, key, crafterBursts, crafterTicksBetweenCrafts, crafterRequirePower, Material.CRAFTER);
    }

    private void scheduleDispenserTick(Block source, BlockKey key) {
        scheduleMachineTick(source, key, dispenserBursts, dispenserTicksBetweenActions, dispenserRequirePower,
                Material.DISPENSER, Material.DROPPER);
    }

    private void scheduleMachineTick(
            Block source,
            BlockKey key,
            Map<BlockKey, Integer> bursts,
            long delay,
            boolean requirePower,
            Material... validTypes
    ) {
        World world = source.getWorld();
        int x = source.getX();
        int y = source.getY();
        int z = source.getZ();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block block = world.getBlockAt(x, y, z);
            if (!isValidType(block.getType(), validTypes) || (requirePower && !isPowered(block))) {
                bursts.remove(key);
                return;
            }

            Integer before = bursts.get(key);
            if (before == null) return;

            block.tick();

            Integer after = bursts.get(key);
            if (after != null && after.equals(before)) {
                bursts.remove(key);
            }
        }, delay);
    }

    private boolean isPowered(Block block) {
        return block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    private boolean isValidType(Material type, Material[] validTypes) {
        for (Material validType : validTypes) {
            if (type == validType) return true;
        }
        return false;
    }

    private void applyFurnaceBoost(Block block) {
        Material type = block.getType();
        if (type != Material.FURNACE && type != Material.BLAST_FURNACE && type != Material.SMOKER) return;
        if (!(block.getState(false) instanceof Furnace furnace)) return;

        boolean changed = false;
        if (furnace.getCookSpeedMultiplier() != furnaceCookSpeedMultiplier) {
            furnace.setCookSpeedMultiplier(furnaceCookSpeedMultiplier);
            changed = true;
        }

        int cookTimeTotal = furnace.getCookTimeTotal();
        if (cookTimeTotal > 1) {
            int boosted = boostedCookTime(cookTimeTotal);
            if (boosted < cookTimeTotal) {
                furnace.setCookTimeTotal(boosted);
                changed = true;
            }
        }

        if (changed) {
            furnace.update(true, false);
        }
    }

    private int boostedCookTime(int totalCookTime) {
        return Math.max(1, (int) Math.ceil(totalCookTime / furnaceCookSpeedMultiplier));
    }

    private int boostedBurnTime(int burnTime) {
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.ceil(burnTime * furnaceFuelTimeMultiplier)));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
