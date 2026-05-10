package fr.smp.ptr;

import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class PtrBlocks {

    private static final Map<String, Block> REGISTERED = new LinkedHashMap<>();

    public static final Block RUNESTEEL = register("runesteel", props ->
            new SimplePolymerBlock(props
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .strength(4.0f, 6.0f)
                    .lightLevel(state -> 12),
                    Blocks.IRON_BLOCK));

    public static final Block PLASMA_BLOCK = register("plasma_block", props ->
            new SimplePolymerBlock(props
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .sound(SoundType.GLASS)
                    .strength(1.5f, 3.0f)
                    .lightLevel(state -> 15),
                    Blocks.SEA_LANTERN));

    public static final Block DARKSTONE = register("darkstone", props ->
            new SimplePolymerBlock(props
                    .mapColor(MapColor.COLOR_BLACK)
                    .sound(SoundType.STONE)
                    .strength(2.5f, 6.0f),
                    Blocks.BLACKSTONE));

    public static final Block GLOW_MOSS = register("glow_moss", props ->
            new SimplePolymerBlock(props
                    .mapColor(MapColor.COLOR_GREEN)
                    .sound(SoundType.MOSS)
                    .strength(0.5f, 0.5f)
                    .lightLevel(state -> 9),
                    Blocks.MOSS_BLOCK));

    public static final Block BLUE_GRASS = register("blue_grass", props ->
            new SimplePolymerBlock(props
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .sound(SoundType.GRASS)
                    .strength(0.6f, 0.6f),
                    Blocks.GRASS_BLOCK));

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory) {
        Identifier id = PtrShowcase.id(name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of().setId(key);
        T block = factory.apply(props);
        T registered = (T) Registry.register(BuiltInRegistries.BLOCK, id, block);
        REGISTERED.put(name, registered);
        return registered;
    }

    public static int count() {
        return REGISTERED.size();
    }

    public static void bootstrap() {
        // class-load triggers static initialisers above
    }

    private PtrBlocks() {}
}
