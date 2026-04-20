package fr.smp.core.alchemytotem;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Mapping ingredient -> effet, aligné sur l'alchimie vanilla.
 * L'amplifier est 0-indexé : 1 = niveau II.
 * On exclut volontairement les effets néfastes/instantanés pour garder l'item fun.
 */
public enum AlchemyEffect {
    STRENGTH(Material.BLAZE_POWDER, PotionEffectType.STRENGTH, 1, "Force II", "#ff5555"),
    REGENERATION(Material.GHAST_TEAR, PotionEffectType.REGENERATION, 1, "Régénération II", "#ff79c6"),
    SPEED(Material.SUGAR, PotionEffectType.SPEED, 1, "Vitesse II", "#7cb0ff"),
    JUMP_BOOST(Material.RABBIT_FOOT, PotionEffectType.JUMP_BOOST, 1, "Saut II", "#a3ff7c"),
    FIRE_RESISTANCE(Material.MAGMA_CREAM, PotionEffectType.FIRE_RESISTANCE, 0, "Résistance au feu", "#ffaa44"),
    NIGHT_VISION(Material.GOLDEN_CARROT, PotionEffectType.NIGHT_VISION, 0, "Vision nocturne", "#ffd866"),
    WATER_BREATHING(Material.PUFFERFISH, PotionEffectType.WATER_BREATHING, 0, "Respiration aquatique", "#5fffe0"),
    SLOW_FALLING(Material.PHANTOM_MEMBRANE, PotionEffectType.SLOW_FALLING, 0, "Chute ralentie", "#c8c8ff"),
    RESISTANCE(Material.TURTLE_SCUTE, PotionEffectType.RESISTANCE, 1, "Résistance II", "#88ff88");

    private final Material ingredient;
    private final PotionEffectType type;
    private final int amplifier;
    private final String display;
    private final String colorHex;

    AlchemyEffect(Material ingredient, PotionEffectType type, int amplifier, String display, String colorHex) {
        this.ingredient = ingredient;
        this.type = type;
        this.amplifier = amplifier;
        this.display = display;
        this.colorHex = colorHex;
    }

    public Material ingredient() { return ingredient; }
    public PotionEffectType type() { return type; }
    public int amplifier() { return amplifier; }
    public String display() { return display; }
    public String colorHex() { return colorHex; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static AlchemyEffect byId(String id) {
        if (id == null) return null;
        try { return AlchemyEffect.valueOf(id.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
