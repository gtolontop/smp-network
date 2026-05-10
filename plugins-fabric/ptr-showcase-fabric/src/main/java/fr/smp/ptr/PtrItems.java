package fr.smp.ptr;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class PtrItems {

    private static final Map<String, Item> REGISTERED = new LinkedHashMap<>();

    public static final Item FOREUSE = registerItem("foreuse",
            props -> new SimplePolymerItem(props.fireResistant().stacksTo(1), Items.IRON_PICKAXE));

    public static final Item TRONCONNEUSE = registerItem("tronconneuse",
            props -> new SimplePolymerItem(props.fireResistant().stacksTo(1), Items.IRON_AXE));

    public static final Item GRAPPIN = registerItem("grappin",
            props -> new SimplePolymerItem(props.stacksTo(1), Items.FISHING_ROD));

    public static final Item SCANNER = registerItem("scanner",
            props -> new SimplePolymerItem(props.stacksTo(1), Items.SPYGLASS));

    public static final Item VOIDSTONE = registerItem("voidstone",
            props -> new SimplePolymerItem(props.stacksTo(64), Items.AMETHYST_SHARD));

    public static final Item BOUSSOLE_BOSS = registerItem("boussole_boss",
            props -> new SimplePolymerItem(props.stacksTo(1), Items.RECOVERY_COMPASS));

    public static final Item MARTEAU_BUILD = registerItem("marteau_build",
            props -> new SimplePolymerItem(props.fireResistant().stacksTo(1), Items.GOLDEN_PICKAXE));

    public static final Item TOTEM_ALARME = registerItem("totem_alarme",
            props -> new SimplePolymerItem(props.stacksTo(1), Items.TOTEM_OF_UNDYING));

    public static final BlockItem RUNESTEEL_ITEM = registerBlockItem("runesteel",
            PtrBlocks.RUNESTEEL, Items.IRON_BLOCK);

    public static final BlockItem PLASMA_BLOCK_ITEM = registerBlockItem("plasma_block",
            PtrBlocks.PLASMA_BLOCK, Items.SEA_LANTERN);

    public static final BlockItem DARKSTONE_ITEM = registerBlockItem("darkstone",
            PtrBlocks.DARKSTONE, Items.BLACKSTONE);

    public static final BlockItem GLOW_MOSS_ITEM = registerBlockItem("glow_moss",
            PtrBlocks.GLOW_MOSS, Items.MOSS_BLOCK);

    public static final BlockItem BLUE_GRASS_ITEM = registerBlockItem("blue_grass",
            PtrBlocks.BLUE_GRASS, Items.GRASS_BLOCK);

    @SuppressWarnings("unchecked")
    private static <T extends Item> T registerItem(String name, Function<Item.Properties, T> factory) {
        Identifier id = PtrShowcase.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().setId(key);
        T item = factory.apply(props);
        T registered = (T) Registry.register(BuiltInRegistries.ITEM, id, item);
        REGISTERED.put(name, registered);
        return registered;
    }

    private static BlockItem registerBlockItem(String name, Block block, Item carrier) {
        Identifier id = PtrShowcase.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().useBlockDescriptionPrefix().setId(key);
        BlockItem item = new PolymerBlockItem(block, props, carrier);
        BlockItem registered = (BlockItem) Registry.register(BuiltInRegistries.ITEM, id, item);
        REGISTERED.put(name, registered);
        return registered;
    }

    public static int count() {
        return REGISTERED.size();
    }

    public static void bootstrap() {
        // class-load triggers static initialisers above
    }

    private PtrItems() {}
}
