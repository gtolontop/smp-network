package fr.smp.core.sell;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

/**
 * 9 categories used by the tier-sell snowball system.
 *
 * Each category has its own progression: the more items you sell, the higher
 * the tier you reach for that specific category, and the better the multiplier
 * the next sale is going to apply (across /sell gui, /sell hand, /sell all,
 * /sellauto, sell-sticks).
 *
 * The mapping is defined inline so that adding a new vanilla material is just
 * a one-line change, and so that the GUI never has to read a yaml file.
 */
public enum SellCategory {
    MINING ("Minerais",        Material.IRON_PICKAXE,    "<#67d8ff>"),
    GEMS   ("Gemmes & Magie",  Material.AMETHYST_CLUSTER,"<#d28dff>"),
    FARMING("Cultures",        Material.WHEAT,           "<#fcd34d>"),
    NATURE ("Bois & Nature",   Material.OAK_SAPLING,     "<#86efac>"),
    STONE  ("Pierre & Terrain",Material.COBBLESTONE,     "<#9ca3af>"),
    NETHER ("Nether",          Material.NETHERRACK,      "<#fb7185>"),
    END    ("End",             Material.END_STONE,       "<#fde68a>"),
    MOBS   ("Mobs & Élevage",  Material.BONE,            "<#f4a8a8>"),
    OCEAN  ("Océan",           Material.PRISMARINE_SHARD,"<#5eead4>");

    private final String displayName;
    private final Material icon;
    private final String colorTag;

    SellCategory(String displayName, Material icon, String colorTag) {
        this.displayName = displayName;
        this.icon = icon;
        this.colorTag = colorTag;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String colorTag() { return colorTag; }

    private static final Map<Material, SellCategory> MAP = new EnumMap<>(Material.class);

    static {
        // ============================================================
        // MINING — raw ores, ore blocks, ingots, nuggets, mineral blocks
        // ============================================================
        register(SellCategory.MINING,
                Material.COAL, Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER,
                Material.DIAMOND, Material.EMERALD, Material.NETHERITE_SCRAP, Material.ANCIENT_DEBRIS,
                Material.FLINT,
                Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT,
                Material.NETHERITE_INGOT,
                Material.IRON_NUGGET, Material.GOLD_NUGGET,
                Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.COPPER_ORE,
                Material.DIAMOND_ORE, Material.EMERALD_ORE,
                Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
                Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_COPPER_ORE,
                Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.COAL_BLOCK, Material.IRON_BLOCK, Material.GOLD_BLOCK,
                Material.COPPER_BLOCK, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK,
                Material.NETHERITE_BLOCK,
                Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK,
                // copper oxidation / waxed variants
                Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER,
                Material.WAXED_COPPER_BLOCK, Material.WAXED_EXPOSED_COPPER,
                Material.WAXED_WEATHERED_COPPER, Material.WAXED_OXIDIZED_COPPER,
                Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER,
                Material.OXIDIZED_CUT_COPPER,
                Material.WAXED_CUT_COPPER, Material.WAXED_EXPOSED_CUT_COPPER,
                Material.WAXED_WEATHERED_CUT_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER
        );

        // ============================================================
        // GEMS — amethyst, prismarine, lapis, redstone, quartz, sculk,
        // froglights, glowstone, magic loot (heart of sea, nautilus,
        // potions, xp bottles, trim templates, banner patterns)
        // ============================================================
        register(SellCategory.GEMS,
                Material.AMETHYST_SHARD, Material.AMETHYST_BLOCK, Material.AMETHYST_CLUSTER,
                Material.LARGE_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.SMALL_AMETHYST_BUD,
                Material.GLOWSTONE_DUST, Material.GLOWSTONE,
                Material.LAPIS_LAZULI, Material.LAPIS_BLOCK,
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.REDSTONE, Material.REDSTONE_BLOCK,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                Material.QUARTZ, Material.QUARTZ_BLOCK,
                Material.NETHER_QUARTZ_ORE,
                Material.PRISMARINE_CRYSTALS,
                Material.ECHO_SHARD,
                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_SENSOR,
                Material.SCULK_SHRIEKER, Material.SCULK_CATALYST,
                Material.OCHRE_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.VERDANT_FROGLIGHT,
                Material.HEART_OF_THE_SEA,
                Material.EXPERIENCE_BOTTLE,
                Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
                Material.NETHER_STAR,
                Material.HEAVY_CORE, Material.OMINOUS_BOTTLE,
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.GLOBE_BANNER_PATTERN, Material.PIGLIN_BANNER_PATTERN,
                Material.FLOW_BANNER_PATTERN, Material.GUSTER_BANNER_PATTERN,
                Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_APPLE
        );

        // ============================================================
        // FARMING — crops, seeds, foods derived from crops
        // ============================================================
        register(SellCategory.FARMING,
                Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
                Material.MELON_SLICE, Material.MELON, Material.PUMPKIN, Material.APPLE,
                Material.SUGAR_CANE, Material.SUGAR, Material.CACTUS, Material.BAMBOO,
                Material.KELP, Material.DRIED_KELP, Material.DRIED_KELP_BLOCK,
                Material.COCOA_BEANS, Material.SWEET_BERRIES, Material.GLOW_BERRIES,
                Material.NETHER_WART, Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK,
                Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.PUMPKIN_SEEDS,
                Material.MELON_SEEDS, Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD,
                Material.TORCHFLOWER, Material.PITCHER_PLANT,
                Material.HAY_BLOCK, Material.BREAD, Material.GOLDEN_CARROT, Material.BAKED_POTATO,
                Material.PUMPKIN_PIE, Material.COOKIE, Material.CAKE,
                Material.BEETROOT_SOUP, Material.MUSHROOM_STEW, Material.RABBIT_STEW,
                Material.SUSPICIOUS_STEW, Material.POISONOUS_POTATO,
                Material.BROWN_MUSHROOM, Material.RED_MUSHROOM,
                Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK, Material.MUSHROOM_STEM
        );

        // ============================================================
        // NATURE — wood, leaves, saplings, flowers, mosses, vines, bees
        // ============================================================
        register(SellCategory.NATURE,
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
                Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.CHERRY_LOG,
                Material.MANGROVE_LOG, Material.PALE_OAK_LOG,
                Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG,
                Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
                Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_PALE_OAK_LOG,
                Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD,
                Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.CHERRY_WOOD,
                Material.MANGROVE_WOOD, Material.PALE_OAK_WOOD,
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS,
                Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.CHERRY_PLANKS,
                Material.MANGROVE_PLANKS, Material.PALE_OAK_PLANKS, Material.BAMBOO_PLANKS,
                Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
                Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.CHERRY_LEAVES,
                Material.MANGROVE_LEAVES, Material.PALE_OAK_LEAVES,
                Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
                Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
                Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
                Material.CHERRY_SAPLING, Material.PALE_OAK_SAPLING, Material.MANGROVE_PROPAGULE,
                // flowers
                Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
                Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP,
                Material.PINK_TULIP, Material.OXEYE_DAISY, Material.CORNFLOWER,
                Material.LILY_OF_THE_VALLEY, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH,
                Material.PEONY, Material.WITHER_ROSE,
                Material.AZALEA, Material.FLOWERING_AZALEA,
                Material.BUSH, Material.FIREFLY_BUSH, Material.PINK_PETALS, Material.WILDFLOWERS,
                Material.OPEN_EYEBLOSSOM, Material.CLOSED_EYEBLOSSOM,
                Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
                Material.SHORT_DRY_GRASS, Material.TALL_DRY_GRASS,
                Material.VINE, Material.DEAD_BUSH, Material.LILY_PAD,
                Material.BIG_DRIPLEAF, Material.SMALL_DRIPLEAF, Material.SPORE_BLOSSOM,
                Material.HANGING_ROOTS, Material.GLOW_LICHEN,
                Material.MOSS_BLOCK, Material.MOSS_CARPET, Material.PALE_MOSS_BLOCK,
                Material.PALE_HANGING_MOSS, Material.PALE_MOSS_CARPET,
                Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS,
                Material.SEA_PICKLE,
                Material.BEE_NEST, Material.BEEHIVE, Material.HONEYCOMB, Material.HONEYCOMB_BLOCK,
                Material.HONEY_BLOCK, Material.HONEY_BOTTLE
        );

        // ============================================================
        // STONE — terrain blocks, dirt, sand, gravel, ice, obsidian
        // ============================================================
        register(SellCategory.STONE,
                Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
                Material.CHISELED_STONE_BRICKS, Material.SMOOTH_STONE,
                Material.GRANITE, Material.POLISHED_GRANITE,
                Material.DIORITE, Material.POLISHED_DIORITE,
                Material.ANDESITE, Material.POLISHED_ANDESITE,
                Material.TUFF, Material.POLISHED_TUFF, Material.TUFF_BRICKS, Material.CHISELED_TUFF,
                Material.CALCITE,
                Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
                Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
                Material.CRACKED_DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_TILES,
                Material.CHISELED_DEEPSLATE,
                Material.DIRT, Material.COARSE_DIRT, Material.GRASS_BLOCK, Material.PODZOL,
                Material.MYCELIUM, Material.ROOTED_DIRT, Material.DIRT_PATH,
                Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY, Material.CLAY_BALL,
                Material.MUD, Material.PACKED_MUD, Material.MUD_BRICKS,
                Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
                Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
                Material.SNOW, Material.SNOW_BLOCK, Material.SNOWBALL,
                Material.POINTED_DRIPSTONE, Material.DRIPSTONE_BLOCK,
                Material.COBWEB,
                Material.SANDSTONE, Material.RED_SANDSTONE,
                Material.SMOOTH_SANDSTONE, Material.SMOOTH_RED_SANDSTONE,
                Material.CHISELED_SANDSTONE, Material.CHISELED_RED_SANDSTONE,
                Material.CUT_SANDSTONE, Material.CUT_RED_SANDSTONE,
                // pottery sherds (extracted by brushing suspicious sand/gravel)
                Material.ANGLER_POTTERY_SHERD, Material.ARCHER_POTTERY_SHERD,
                Material.ARMS_UP_POTTERY_SHERD, Material.BLADE_POTTERY_SHERD,
                Material.BREWER_POTTERY_SHERD, Material.BURN_POTTERY_SHERD,
                Material.DANGER_POTTERY_SHERD, Material.EXPLORER_POTTERY_SHERD,
                Material.FLOW_POTTERY_SHERD, Material.FRIEND_POTTERY_SHERD,
                Material.GUSTER_POTTERY_SHERD, Material.HEART_POTTERY_SHERD,
                Material.HEARTBREAK_POTTERY_SHERD, Material.HOWL_POTTERY_SHERD,
                Material.MINER_POTTERY_SHERD, Material.MOURNER_POTTERY_SHERD,
                Material.PLENTY_POTTERY_SHERD, Material.PRIZE_POTTERY_SHERD,
                Material.SCRAPE_POTTERY_SHERD, Material.SHEAF_POTTERY_SHERD,
                Material.SHELTER_POTTERY_SHERD, Material.SKULL_POTTERY_SHERD,
                Material.SNORT_POTTERY_SHERD,
                Material.BELL
        );

        // ============================================================
        // NETHER — netherrack, basalt, blackstone, blaze, ghast, magma,
        // crimson/warped, nether wart, soul soil, breeze rod
        // ============================================================
        register(SellCategory.NETHER,
                Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL,
                Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.GILDED_BLACKSTONE,
                Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT,
                Material.NETHER_GOLD_ORE,
                Material.BLAZE_ROD, Material.BLAZE_POWDER, Material.BREEZE_ROD,
                Material.MAGMA_CREAM, Material.MAGMA_BLOCK, Material.GHAST_TEAR,
                Material.CRIMSON_STEM, Material.WARPED_STEM,
                Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
                Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
                Material.CRIMSON_PLANKS, Material.WARPED_PLANKS,
                Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS,
                Material.CRIMSON_ROOTS, Material.WARPED_ROOTS,
                Material.NETHER_SPROUTS, Material.WEEPING_VINES, Material.TWISTING_VINES,
                Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM,
                Material.SHROOMLIGHT,
                Material.SOUL_TORCH, Material.SOUL_LANTERN, Material.SOUL_CAMPFIRE,
                Material.WITHER_SKELETON_SKULL,
                Material.PIGLIN_HEAD,
                Material.NETHER_BRICK, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS,
                Material.CRACKED_NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS,
                // trial chamber loot is mostly Overworld now but let's keep musical/exotic mixed
                Material.MUSIC_DISC_PIGSTEP, Material.MUSIC_DISC_RELIC
        );

        // ============================================================
        // END — end stone, ender pearls, chorus, shulker, dragon stuff
        // ============================================================
        register(SellCategory.END,
                Material.END_STONE, Material.END_STONE_BRICKS,
                Material.PURPUR_BLOCK, Material.PURPUR_PILLAR,
                Material.ENDER_PEARL, Material.ENDER_EYE,
                Material.CHORUS_FRUIT, Material.POPPED_CHORUS_FRUIT,
                Material.CHORUS_FLOWER, Material.CHORUS_PLANT,
                Material.SHULKER_SHELL, Material.DRAGON_BREATH,
                Material.DRAGON_EGG, Material.DRAGON_HEAD,
                Material.ELYTRA,
                Material.END_ROD, Material.END_CRYSTAL,
                Material.PHANTOM_MEMBRANE
        );

        // ============================================================
        // MOBS — hostile drops, livestock, eggs, leather, scutes, heads,
        // music discs, totem, trial keys, goat horns
        // ============================================================
        register(SellCategory.MOBS,
                Material.ROTTEN_FLESH, Material.BONE, Material.BONE_BLOCK, Material.BONE_MEAL,
                Material.STRING, Material.SPIDER_EYE, Material.FERMENTED_SPIDER_EYE,
                Material.GUNPOWDER, Material.SLIME_BALL, Material.SLIME_BLOCK,
                Material.FEATHER, Material.LEATHER,
                Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON, Material.RABBIT,
                Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
                Material.COOKED_MUTTON, Material.COOKED_RABBIT,
                Material.RABBIT_HIDE, Material.RABBIT_FOOT,
                Material.EGG, Material.BLUE_EGG, Material.BROWN_EGG,
                Material.ARMADILLO_SCUTE, Material.TURTLE_SCUTE,
                Material.INK_SAC, Material.GLOW_INK_SAC,
                Material.MILK_BUCKET, Material.AXOLOTL_BUCKET, Material.TADPOLE_BUCKET,
                Material.TOTEM_OF_UNDYING,
                Material.GOAT_HORN,
                Material.ZOMBIE_HEAD, Material.SKELETON_SKULL, Material.CREEPER_HEAD,
                Material.SNIFFER_EGG, Material.TURTLE_EGG,
                // wool drops from sheep / dye output
                Material.WHITE_WOOL, Material.LIGHT_GRAY_WOOL, Material.GRAY_WOOL,
                Material.BLACK_WOOL, Material.BROWN_WOOL, Material.RED_WOOL,
                Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
                Material.GREEN_WOOL, Material.CYAN_WOOL, Material.LIGHT_BLUE_WOOL,
                Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL, Material.PINK_WOOL,
                // chainmail (zombie/skeleton drop)
                Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
                Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                // horse armors (rare loot)
                Material.COPPER_HORSE_ARMOR, Material.IRON_HORSE_ARMOR,
                Material.GOLDEN_HORSE_ARMOR, Material.DIAMOND_HORSE_ARMOR,
                // music discs (creeper drops)
                Material.MUSIC_DISC_11, Material.MUSIC_DISC_13, Material.MUSIC_DISC_BLOCKS,
                Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_CHIRP, Material.MUSIC_DISC_FAR,
                Material.MUSIC_DISC_MALL, Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_STAL,
                Material.MUSIC_DISC_STRAD, Material.MUSIC_DISC_WARD, Material.MUSIC_DISC_OTHERSIDE,
                Material.MUSIC_DISC_CREATOR, Material.MUSIC_DISC_CREATOR_MUSIC_BOX,
                Material.MUSIC_DISC_PRECIPICE, Material.MUSIC_DISC_TEARS,
                Material.MUSIC_DISC_LAVA_CHICKEN, Material.MUSIC_DISC_WAIT,
                Material.DISC_FRAGMENT_5,
                // trial keys (mob arena loot)
                Material.TRIAL_KEY, Material.OMINOUS_TRIAL_KEY,
                // saddle = horse-related
                Material.SADDLE
        );

        // ============================================================
        // OCEAN — fish, coral, water, sponge, prismarine, trident
        // ============================================================
        register(SellCategory.OCEAN,
                Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH,
                Material.COOKED_COD, Material.COOKED_SALMON,
                Material.COD_BUCKET, Material.SALMON_BUCKET,
                Material.TROPICAL_FISH_BUCKET, Material.PUFFERFISH_BUCKET,
                Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.POWDER_SNOW_BUCKET,
                Material.SPONGE, Material.WET_SPONGE,
                Material.SEAGRASS, Material.NAUTILUS_SHELL,
                Material.PRISMARINE_SHARD,
                Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
                Material.SEA_LANTERN,
                Material.TRIDENT,
                Material.TUBE_CORAL, Material.BRAIN_CORAL, Material.BUBBLE_CORAL,
                Material.FIRE_CORAL, Material.HORN_CORAL,
                Material.TUBE_CORAL_FAN, Material.BRAIN_CORAL_FAN, Material.BUBBLE_CORAL_FAN,
                Material.FIRE_CORAL_FAN, Material.HORN_CORAL_FAN,
                Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK,
                Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK,
                Material.DEAD_TUBE_CORAL, Material.DEAD_BRAIN_CORAL, Material.DEAD_BUBBLE_CORAL,
                Material.DEAD_FIRE_CORAL, Material.DEAD_HORN_CORAL,
                Material.DEAD_TUBE_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN,
                Material.DEAD_FIRE_CORAL_FAN, Material.DEAD_HORN_CORAL_FAN,
                Material.DEAD_TUBE_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK,
                Material.DEAD_BUBBLE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK,
                Material.DEAD_HORN_CORAL_BLOCK
        );
    }

    private static void register(SellCategory cat, Material... mats) {
        for (Material m : mats) {
            if (m == null) continue;
            MAP.put(m, cat);
        }
    }

    /**
     * Resolve the category for an item. Returns {@code null} if the material
     * is not part of any of the 9 tracked categories — those items still sell
     * at base worth, but do not contribute to (or benefit from) any tier.
     */
    public static SellCategory of(Material m) {
        return m == null ? null : MAP.get(m);
    }
}
