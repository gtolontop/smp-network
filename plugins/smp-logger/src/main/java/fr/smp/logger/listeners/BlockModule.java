package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Logs every block mutation. EventPriority.MONITOR + ignoreCancelled means we
 * record what actually happened, not what was attempted.
 *
 * Special-case calls hand off to RareResourceTracker for diamond/netherite/etc.
 * via SMPLogger.rareTracker().
 */
public class BlockModule implements Listener {

    private final SMPLogger plugin;

    public BlockModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        EventBuilder.begin(plugin)
                .action(Action.BLOCK_BREAK)
                .actor(p)
                .at(b)
                .material(b.getType())
                .amount(1)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        EventBuilder.begin(plugin)
                .action(Action.BLOCK_PLACE)
                .actor(p)
                .at(b)
                .material(b.getType())
                .amount(1)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            EventBuilder.begin(plugin)
                    .action(Action.BLOCK_EXPLODE)
                    .at(b)
                    .material(b.getType())
                    .submit();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Entity src = e.getEntity();
        for (Block b : e.blockList()) {
            EventBuilder eb = EventBuilder.begin(plugin)
                    .action(Action.BLOCK_EXPLODE)
                    .at(b)
                    .material(b.getType())
                    .meta(src.getType().ordinal());
            if (src instanceof Player p) eb.actor(p);
            eb.submit();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        Block b = e.getBlock();
        EventBuilder.begin(plugin)
                .action(Action.BLOCK_BURN)
                .at(b)
                .material(b.getType())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        Block b = e.getBlock();
        EventBuilder.begin(plugin)
                .action(Action.BLOCK_FADE)
                .at(b)
                .material(b.getType())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent e) {
        Block b = e.getBlock();
        EventBuilder.begin(plugin)
                .action(Action.LEAF_DECAY)
                .at(b)
                .material(b.getType())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent e) {
        Block from = e.getBlock();
        Block to = e.getToBlock();
        EventBuilder.begin(plugin)
                .action(Action.LIQUID_FLOW)
                .at(to)
                .material(from.getType())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            EventBuilder.begin(plugin)
                    .action(Action.PISTON_EXTEND)
                    .at(b)
                    .material(b.getType())
                    .submit();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            EventBuilder.begin(plugin)
                    .action(Action.PISTON_RETRACT)
                    .at(b)
                    .material(b.getType())
                    .submit();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        Player p = e.getPlayer();
        for (org.bukkit.block.BlockState bs : e.getBlocks()) {
            EventBuilder eb = EventBuilder.begin(plugin)
                    .action(Action.STRUCTURE_GROW)
                    .at(bs.getBlock())
                    .material(bs.getType());
            if (p != null) eb.actor(p);
            eb.submit();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        EventBuilder.begin(plugin)
                .action(Action.BLOCK_FORM)
                .at(e.getBlock())
                .material(e.getNewState().getType())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        EventBuilder eb = EventBuilder.begin(plugin)
                .action(Action.ENTITY_CHANGE_BLOCK)
                .at(e.getBlock())
                .material(e.getTo())
                .meta(e.getEntity().getType().ordinal());
        if (e.getEntity() instanceof Player p) eb.actor(p);
        eb.submit();
    }
}
