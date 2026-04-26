package fr.smp.core.enchants;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.Locale;
import java.util.Optional;

/**
 * Registry of all SMP custom enchants.
 *
 * Two flavours:
 *  - OVERCAP: wraps a vanilla enchant applied above its normal max level.
 *    The item carries the real vanilla enchant (unsafe-applied) — vanilla
 *    mining/damage/protection formulas still apply, so no listener work
 *    is needed for the effect itself.
 *  - CUSTOM: stored in a PDC tag on the item, handled by listeners.
 */
public enum CustomEnchant {

    // ── Over-cap vanilla ──────────────────────────────────────────────
    EFF_VI ("efficacite_6", "Efficacité",      6, Flavour.OVERCAP, Target.DIG_TOOLS,  Enchantment.EFFICIENCY,
            "<gray>Vitesse de minage délirante. Niveau VI.</gray>"),
    UNB_IV ("solidite_4",   "Solidité",        4, Flavour.OVERCAP, Target.BREAKABLE,  Enchantment.UNBREAKING,
            "<gray>Durabilité bien au-delà du normal. Niveau IV.</gray>"),
    PROT_VI("protection_6", "Protection",      6, Flavour.OVERCAP, Target.ARMOR,      Enchantment.PROTECTION,
            "<gray>Réduction de dégâts maximale. Niveau VI.</gray>"),
    SHARP_VI("tranchant_6", "Tranchant",       6, Flavour.OVERCAP, Target.MELEE,      Enchantment.SHARPNESS,
            "<gray>Dégâts mêlée supérieurs. Niveau VI.</gray>"),
    FORT_IV ("fortune_4",   "Fortune",         4, Flavour.OVERCAP, Target.DIG_TOOLS,  Enchantment.FORTUNE,
            "<gray>Multiplicateur de drops supérieur. Niveau IV.</gray>"),

    // ── Custom power enchants ─────────────────────────────────────────
    VEIN   ("filonage",     "Filonage",        1, Flavour.CUSTOM,  Target.PICKAXE,    null,
            "<gray>Casse le filon entier d'un seul coup (jusqu'à 128 blocs).</gray>"),
    QUARRY ("carriere",     "Carrière",        3, Flavour.CUSTOM,  Target.PICKAXE,    null,
            "<gray>Mine une zone 3x3 (I) / 4x4 (II) / 5x5 (III).</gray>"),
    FELLER ("bucheron",     "Bûcheron",        1, Flavour.CUSTOM,  Target.AXE,        null,
            "<gray>Abat l'arbre entier en un coup de hache.</gray>"),
    EXCAV  ("excavateur",   "Excavateur",      3, Flavour.CUSTOM,  Target.SHOVEL,     null,
            "<gray>Creuse une zone 3x3 (I) / 4x4 (II) / 5x5 (III).</gray>"),
    HARVEST("moisson",      "Moisson",         3, Flavour.CUSTOM,  Target.HOE,        null,
            "<gray>Récolte une zone 3x3 (I) / 4x4 (II) / 5x5 (III).</gray>"),
    REPLANT("replantage",   "Replantage",      1, Flavour.CUSTOM,  Target.HOE,        null,
            "<gray>Replante automatiquement chaque culture récoltée.</gray>"),
    SMELT  ("fonte",        "Fonte",           1, Flavour.CUSTOM,  Target.DIG_TOOLS,  null,
            "<gray>Fait fondre minerais et matières à la volée.</gray>"),
    MAGNET ("aimant",       "Aimant",          1, Flavour.CUSTOM,  Target.DIG_TOOLS,  null,
            "<gray>Les drops arrivent directement dans l'inventaire.</gray>"),

    // ── Armor / utility ───────────────────────────────────────────────
    VITAL  ("vitalite",     "Vitalité",        5, Flavour.CUSTOM,  Target.CHEST_SLOT, null,
            "<gray>+2 cœurs d'absorption par niveau. Régénère passivement.</gray>"),
    SOUL   ("ame_liee",     "Lié à l'âme",     1, Flavour.CUSTOM,  Target.BREAKABLE,  null,
            "<gray>Si plusieurs objets l'ont, un seul est gardé à ta mort, au hasard.</gray>");

    public enum Flavour { OVERCAP, CUSTOM }

    public enum Target {
        PICKAXE, AXE, SHOVEL, HOE, DIG_TOOLS, MELEE, ARMOR, CHEST_SLOT, BREAKABLE;

        public boolean matches(Material m) {
            if (m == null) return false;
            String n = m.name();
            boolean pick  = n.endsWith("_PICKAXE");
            boolean axe   = n.endsWith("_AXE") && !n.endsWith("_PICKAXE");
            boolean shov  = n.endsWith("_SHOVEL");
            boolean hoe   = n.endsWith("_HOE");
            boolean sword = n.endsWith("_SWORD");
            boolean tool  = pick || axe || shov || hoe;
            boolean helm  = n.endsWith("_HELMET") || m == Material.TURTLE_HELMET;
            boolean chest = n.endsWith("_CHESTPLATE");
            boolean legs  = n.endsWith("_LEGGINGS");
            boolean boots = n.endsWith("_BOOTS");
            boolean armor = helm || chest || legs || boots;
            boolean elytra= m == Material.ELYTRA;
            return switch (this) {
                case PICKAXE -> pick;
                case AXE -> axe;
                case SHOVEL -> shov;
                case HOE -> hoe;
                case DIG_TOOLS -> tool;
                case MELEE -> sword || axe;
                case ARMOR -> armor || elytra;
                case CHEST_SLOT -> chest || elytra;
                case BREAKABLE -> tool || sword || armor || elytra || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT || m == Material.SHIELD;
            };
        }
    }

    private final String id;
    private final String displayName;
    private final int maxLevel;
    private final Flavour flavour;
    private final Target target;
    private final Enchantment vanilla;
    private final String loreLine;

    CustomEnchant(String id, String displayName, int maxLevel, Flavour flavour,
                  Target target, Enchantment vanilla, String loreLine) {
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.flavour = flavour;
        this.target = target;
        this.vanilla = vanilla;
        this.loreLine = loreLine;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int maxLevel() { return maxLevel; }
    public Flavour flavour() { return flavour; }
    public Target target() { return target; }
    public Enchantment vanilla() { return vanilla; }
    public String loreLine() { return loreLine; }

    public boolean isOvercap() { return flavour == Flavour.OVERCAP; }
    public boolean isCustom()  { return flavour == Flavour.CUSTOM; }

    public static Optional<CustomEnchant> byId(String id) {
        if (id == null) return Optional.empty();
        String norm = id.toLowerCase(Locale.ROOT).replace('-', '_');
        for (CustomEnchant e : values()) if (e.id.equals(norm)) return Optional.of(e);
        return Optional.empty();
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV";
            case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII";
            case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
