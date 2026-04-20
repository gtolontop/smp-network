package fr.smp.anticheat.visibility;

import fr.smp.anticheat.AntiCheatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Invalidates the VisibilityEngine cache for watched positions near a block change,
 * so a broken cover block reveals the ore behind it without waiting for the cache TTL.
 *
 * Deliberately does NOT listen to {@code BlockFromToEvent} (fluid spread): flowing
 * water/lava cannot create new line-of-sight through a solid wall, and fluid ticks
 * fire at a very high rate — listening to them was consuming a majority of the
 * server tick in invalidation scans. Any reveal we miss from fluid changes is picked
 * up by the stale-cache TTL within a few hundred ms (imperceptible).
 *
 * Also does NOT do the "invalidate every watch for this player" paranoia pass: the
 * radius-based invalidate catches the common case (cover block adjacent-ish to the
 * target), and the TTL covers the long-range edge case (breaking a cover block 20
 * blocks away from the target). The paranoia pass rescanned the whole watch set
 * on every break and was the second-largest source of wasted work.
 */
public final class BlockChangeListener implements Listener {

    private static final int INVALIDATE_RADIUS = 12;

    private final AntiCheatPlugin plugin;

    public BlockChangeListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    private void invalidate(Block b) {
        var vis = plugin.visibility();
        if (vis == null) return;
        int touched = vis.invalidateNearby(b.getWorld(), b.getX(), b.getY(), b.getZ(), INVALIDATE_RADIUS);
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[xray-break] " + b.getType() + " at (" + b.getX() + "," + b.getY() + "," + b.getZ()
                    + ") world=" + b.getWorld().getName() + " env=" + b.getWorld().getEnvironment()
                    + " invalidated=" + touched + " watches");
        }
        revealAdjacentMasks(b);
    }

    /**
     * When a block is broken, force-reveal any of its 6 direct neighbors that are
     * masked for any online player in the same world. This is the "mine-next-to"
     * reveal path: LoS raytrace can fail because the eye-to-ore line goes through a
     * different cover block than the one just broken (e.g. user breaks the block at
     * y=15 but the ore is at y=16 — ray at eye height y=16.5 still hits a solid
     * block). Direct-neighbor exposure is a stronger, simpler signal: if any face
     * of the masked ore is now air, it's naturally visible in vanilla, so we
     * unmask it immediately. Matches the user expectation "break the block
     * touching the ore → see the ore".
     */
    private void revealAdjacentMasks(Block broken) {
        var xray = plugin.xray();
        if (xray == null) return;
        World world = broken.getWorld();
        int wx = broken.getX(), wy = broken.getY(), wz = broken.getZ();
        int[][] d = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world)) continue;
            for (int[] n : d) {
                int nx = wx + n[0], ny = wy + n[1], nz = wz + n[2];
                if (xray.isMasked(p, nx, ny, nz)) {
                    xray.revealOnInteract(p, nx, ny, nz);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        var vis = plugin.visibility();
        if (vis == null) return;
        // Explosions can affect many blocks — use a single bounding-box invalidate
        // centered on the explosion origin instead of looping per broken block.
        Block origin = e.getBlock();
        vis.invalidateNearby(origin.getWorld(), origin.getX(), origin.getY(), origin.getZ(),
                INVALIDATE_RADIUS + 6);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        var vis = plugin.visibility();
        if (vis == null) return;
        var loc = e.getLocation();
        vis.invalidateNearby(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                INVALIDATE_RADIUS + 6);
    }
}
