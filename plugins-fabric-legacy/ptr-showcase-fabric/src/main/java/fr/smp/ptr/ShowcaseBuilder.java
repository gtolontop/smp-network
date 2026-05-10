package fr.smp.ptr;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a 32×32 plaza centred on the caller, populated with the PTR
 * showcase content: floor of polished blackstone, item display rows
 * along the +X axis, real custom blocks set as one-block displays along
 * the -X axis, and three boss spawn pads at the corners.
 */
public final class ShowcaseBuilder {

    private static final int RADIUS = 16;

    private ShowcaseBuilder() {}

    public static void build(ServerLevel level, BlockPos centre) {
        floor(level, centre);
        blockRow(level, centre);
        bossSpawnPads(level, centre);
        info(level, centre);
    }

    private static void floor(ServerLevel level, BlockPos centre) {
        BlockState slab = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                boolean border = Math.abs(dx) == RADIUS || Math.abs(dz) == RADIUS;
                BlockPos p = centre.offset(dx, -1, dz);
                level.setBlock(p, border ? gilded : slab, 3);
            }
        }
    }

    private static void blockRow(ServerLevel level, BlockPos centre) {
        // 5 real custom blocks along +X
        net.minecraft.world.level.block.Block[] reals = {
                PtrBlocks.RUNESTEEL, PtrBlocks.PLASMA_BLOCK, PtrBlocks.DARKSTONE,
                PtrBlocks.GLOW_MOSS, PtrBlocks.BLUE_GRASS
        };
        for (int i = 0; i < reals.length; i++) {
            BlockPos p = centre.offset(2 * (i + 1), 0, 0);
            level.setBlock(p, reals[i].defaultBlockState(), 3);
        }
        // 5 furniture blocks along -X
        net.minecraft.world.level.block.Block[] furniture = {
                PtrBlocks.FORGE_RUNIQUE, PtrBlocks.STATION_RECHARGE, PtrBlocks.CONSOLE_MARCHE_NOIR,
                PtrBlocks.PYLONE_TELEGRAPH, PtrBlocks.TABLEAU_EVENTS
        };
        for (int i = 0; i < furniture.length; i++) {
            BlockPos p = centre.offset(-2 * (i + 1), 0, 0);
            level.setBlock(p, furniture[i].defaultBlockState(), 3);
        }
    }

    private static void bossSpawnPads(ServerLevel level, BlockPos centre) {
        // 3 corners marked with respawn-anchor-style materials
        BlockPos[] corners = {
                centre.offset(RADIUS - 2, 0, RADIUS - 2),
                centre.offset(-(RADIUS - 2), 0, RADIUS - 2),
                centre.offset(RADIUS - 2, 0, -(RADIUS - 2)),
        };
        BlockState pad = Blocks.RESPAWN_ANCHOR.defaultBlockState();
        for (BlockPos p : corners) {
            level.setBlock(p, pad, 3);
        }
    }

    private static void info(ServerLevel level, BlockPos centre) {
        Component msg = Component.literal("§6§l[PTR] §fShowcase plaza built at " +
                centre.getX() + ", " + centre.getY() + ", " + centre.getZ() + " §7(use §f/ptr spawn <boss>§7 at the pads)");
        level.players().forEach(p -> {
            if (p.distanceToSqr(centre.getCenter()) <= 64.0 * 64.0) {
                p.sendSystemMessage(msg);
            }
        });
    }
}
