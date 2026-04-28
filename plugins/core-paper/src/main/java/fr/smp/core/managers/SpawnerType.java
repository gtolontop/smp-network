package fr.smp.core.managers;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;

/**
 * Types de spawners custom. Chaque type a une loot-table pondérée,
 * un icône (spawn egg correspondant) et un nom affiché.
 */
public enum SpawnerType {

    ZOMBIE("Zombie", "<green>", Material.ZOMBIE_SPAWN_EGG, Material.ROTTEN_FLESH, EntityType.ZOMBIE, List.of(
            new Drop(Material.ROTTEN_FLESH, 1, 2, 60),
            new Drop(Material.IRON_INGOT, 1, 1, 10),
            new Drop(Material.CARROT, 1, 1, 8),
            new Drop(Material.POTATO, 1, 1, 8)
    )),
    SKELETON("Squelette", "<white>", Material.SKELETON_SPAWN_EGG, Material.BONE, EntityType.SKELETON, List.of(
            new Drop(Material.BONE, 1, 2, 60),
            new Drop(Material.ARROW, 1, 2, 40)
    )),
    CREEPER("Creeper", "<dark_green>", Material.CREEPER_SPAWN_EGG, Material.GUNPOWDER, EntityType.CREEPER, List.of(
            new Drop(Material.GUNPOWDER, 1, 2, 100)
    )),
    SPIDER("Araignée", "<dark_red>", Material.SPIDER_SPAWN_EGG, Material.STRING, EntityType.SPIDER, List.of(
            new Drop(Material.STRING, 1, 2, 70),
            new Drop(Material.SPIDER_EYE, 1, 1, 30)
    )),
    CAVE_SPIDER("Araignée Venimeuse", "<blue>", Material.CAVE_SPIDER_SPAWN_EGG, Material.FERMENTED_SPIDER_EYE, EntityType.CAVE_SPIDER, List.of(
            new Drop(Material.STRING, 1, 2, 60),
            new Drop(Material.SPIDER_EYE, 1, 1, 35),
            new Drop(Material.FERMENTED_SPIDER_EYE, 1, 1, 5)
    )),
    ENDERMAN("Enderman", "<dark_purple>", Material.ENDERMAN_SPAWN_EGG, Material.ENDER_PEARL, EntityType.ENDERMAN, List.of(
            new Drop(Material.ENDER_PEARL, 1, 1, 100)
    )),
    BLAZE("Blaze", "<gold>", Material.BLAZE_SPAWN_EGG, Material.BLAZE_ROD, EntityType.BLAZE, List.of(
            new Drop(Material.BLAZE_ROD, 1, 1, 100)
    )),
    WITCH("Sorcière", "<light_purple>", Material.WITCH_SPAWN_EGG, Material.REDSTONE, EntityType.WITCH, List.of(
            new Drop(Material.REDSTONE, 1, 3, 25),
            new Drop(Material.GLOWSTONE_DUST, 1, 3, 25),
            new Drop(Material.SUGAR, 1, 2, 20),
            new Drop(Material.STICK, 1, 2, 15),
            new Drop(Material.SPIDER_EYE, 1, 1, 10),
            new Drop(Material.GLASS_BOTTLE, 1, 1, 5)
    )),
    GUARDIAN("Gardien", "<aqua>", Material.GUARDIAN_SPAWN_EGG, Material.PRISMARINE_SHARD, EntityType.GUARDIAN, List.of(
            new Drop(Material.PRISMARINE_SHARD, 1, 2, 60),
            new Drop(Material.PRISMARINE_CRYSTALS, 1, 1, 25),
            new Drop(Material.COD, 1, 1, 15)
    )),
    GHAST("Ghast", "<white>", Material.GHAST_SPAWN_EGG, Material.GHAST_TEAR, EntityType.GHAST, List.of(
            new Drop(Material.GHAST_TEAR, 1, 1, 30),
            new Drop(Material.GUNPOWDER, 1, 2, 70)
    )),
    PIGLIN("Piglin", "<gold>", Material.PIGLIN_SPAWN_EGG, Material.GOLD_INGOT, EntityType.PIGLIN, List.of(
            new Drop(Material.GOLD_INGOT, 1, 1, 30),
            new Drop(Material.GOLD_NUGGET, 1, 4, 50),
            new Drop(Material.CRIMSON_FUNGUS, 1, 1, 20)
    )),
    SLIME("Slime", "<green>", Material.SLIME_SPAWN_EGG, Material.SLIME_BALL, EntityType.SLIME, List.of(
            new Drop(Material.SLIME_BALL, 1, 2, 100)
    )),
    MAGMA_CUBE("Cube de Magma", "<red>", Material.MAGMA_CUBE_SPAWN_EGG, Material.MAGMA_CREAM, EntityType.MAGMA_CUBE, List.of(
            new Drop(Material.MAGMA_CREAM, 1, 1, 100)
    )),
    IRON_GOLEM("Golem de Fer", "<gray>", Material.IRON_GOLEM_SPAWN_EGG, Material.IRON_INGOT, EntityType.IRON_GOLEM, List.of(
            new Drop(Material.IRON_INGOT, 1, 2, 85),
            new Drop(Material.POPPY, 1, 1, 15)
    )),
    COW("Vache", "<gold>", Material.COW_SPAWN_EGG, Material.LEATHER, EntityType.COW, List.of(
            new Drop(Material.LEATHER, 1, 2, 50),
            new Drop(Material.BEEF, 1, 2, 50)
    )),
    PIG("Cochon", "<light_purple>", Material.PIG_SPAWN_EGG, Material.PORKCHOP, EntityType.PIG, List.of(
            new Drop(Material.PORKCHOP, 1, 2, 100)
    )),
    CHICKEN("Poulet", "<yellow>", Material.CHICKEN_SPAWN_EGG, Material.CHICKEN, EntityType.CHICKEN, List.of(
            new Drop(Material.FEATHER, 1, 2, 50),
            new Drop(Material.CHICKEN, 1, 1, 35),
            new Drop(Material.EGG, 1, 1, 15)
    )),
    SHEEP("Mouton", "<white>", Material.SHEEP_SPAWN_EGG, Material.WHITE_WOOL, EntityType.SHEEP, List.of(
            new Drop(Material.MUTTON, 1, 2, 55),
            new Drop(Material.WHITE_WOOL, 1, 1, 45)
    )),
    SILVERFISH("Poisson d'Argent", "<gray>", Material.SILVERFISH_SPAWN_EGG, Material.STONE, EntityType.SILVERFISH, List.of(
            new Drop(Material.COBBLESTONE, 1, 2, 55),
            new Drop(Material.STONE, 1, 1, 35),
            new Drop(Material.EXPERIENCE_BOTTLE, 1, 1, 10)
    ));

    /** Une entrée de loot: matériau, quantité min-max, poids relatif. */
    public record Drop(Material material, int min, int max, int weight) {}

    private final String display;
    private final String colorTag;
    private final Material icon;
    private final Material fallbackIcon;
    private final EntityType entityType;
    private final List<Drop> loot;
    private final int totalWeight;

    SpawnerType(String display, String colorTag, Material icon, Material fallbackIcon, EntityType entityType, List<Drop> loot) {
        this.display = display;
        this.colorTag = colorTag;
        this.icon = icon;
        this.fallbackIcon = fallbackIcon;
        this.entityType = entityType;
        this.loot = loot;
        this.totalWeight = loot.stream().mapToInt(Drop::weight).sum();
    }

    public String display() { return display; }
    public String colorTag() { return colorTag; }
    public EntityType entityType() { return entityType; }

    /**
     * Icône utilisé pour représenter le spawner dans les items/GUI.
     * Si le spawn-egg n'existe pas dans cette version du serveur, fallback.
     */
    public Material icon() {
        return icon != null ? icon : fallbackIcon;
    }

    public List<Drop> loot() { return loot; }
    public int totalWeight() { return totalWeight; }

    public static SpawnerType fromId(String id) {
        if (id == null) return null;
        try { return SpawnerType.valueOf(id.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static SpawnerType[] all() { return values(); }

    /** Liste immuable des identifiants (utile pour tab-completion). */
    public static List<String> ids() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
