package fr.smp.anticheat.clients;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Dictionnaire de signatures connues de clients trichés / mods "gris".
 * <p>
 * Trois familles:
 * <ul>
 *   <li>{@link #BLACKLIST_CHANNEL_PATTERNS} — substrings qui, si présents dans un
 *       channel plugin-messaging enregistré par le client, trahissent un cheat.</li>
 *   <li>{@link #BLACKLIST_BRAND_PATTERNS} — substrings dans le brand {@code minecraft:brand}
 *       que seuls des clients trichés embarquent (la plupart spoofent "vanilla").</li>
 *   <li>{@link #GREY_CHANNEL_PATTERNS} — mods "gris" (ESP-adjacents, WDL, freecam)
 *       qui ne sont pas forcément des cheats PvP mais violent le fair-play survie.</li>
 * </ul>
 */
public final class CheatSignatures {

    private CheatSignatures() {}

    public static final List<String> BLACKLIST_CHANNEL_PATTERNS = List.of(
            "meteorclient",
            "meteor-client",
            "meteor:",
            "wurst:",
            "wurstclient",
            "impact:",
            "impactclient",
            "aristois",
            "sigma:",
            "liquidbounce",
            "vape:",
            "vapeclient",
            "blc:cheat",
            "konas:",
            "konasclient",
            "inertia:",
            "rusherhack",
            "future:",
            "rageware"
    );

    public static final List<String> BLACKLIST_BRAND_PATTERNS = List.of(
            "meteor",
            "wurst",
            "impact",
            "aristois",
            "sigma",
            "liquidbounce",
            "vape",
            "konas",
            "inertia",
            "rusherhack",
            "future",
            "rageware",
            "pyro",
            "cheat"
    );

    /** Mods "gris": ESP / minimaps / WDL / freecam — bloqués par défaut. */
    public static final List<String> GREY_CHANNEL_PATTERNS = List.of(
            // World Downloaders — extraction du monde, gravement abusif.
            "wdl:init",
            "wdl|init",
            "worlddownloader",
            // Xaero's minimap/worldmap — révèlent position/chunks explorés.
            "xaerominimap",
            "xaeroworldmap",
            // Freecam mods dédiés.
            "freecam:",
            "fabricfreecam",
            // Bhc / cave finder — révèlent la géométrie des caves.
            "cisco_bhc",
            // Better sprinting — leak d'input côté serveur.
            "bettersprint"
    );

    /**
     * Channels explicitement autorisés. Vérifié AVANT le blacklist/grey — si un
     * channel matche cette liste, il ne peut pas être flagué, point. Utilisé pour
     * les outils de building légitimes (schematic editors).
     *
     * Pour étendre: ajouter un substring suffisamment spécifique pour ne pas
     * recouvrir une signature cheat. Exemple sûr: {@code "litematica"} ne
     * recouvre aucun autre client connu.
     */
    public static final List<String> ALLOWED_CHANNEL_PATTERNS = List.of(
            "litematica",
            "schematica"
    );

    /** Brands connus d'écosystèmes clients non-vanilla. Informatif, pas flag direct. */
    public static final List<String> KNOWN_BRAND_ECOSYSTEMS = List.of(
            "vanilla", "forge", "fabric", "quilt", "neoforge",
            "lunar", "badlion", "labymod", "optifine", "feather"
    );

    public static MatchResult matchChannel(String channel) {
        String lower = channel.toLowerCase(Locale.ROOT);
        // Allowlist: court-circuite toute détection. Intention explicite — si un
        // outil de building autorisé enregistre aussi accessoirement un channel
        // ESP-adjacent, on ne flag pas. Retourne null = canal propre.
        for (String allowed : ALLOWED_CHANNEL_PATTERNS) {
            if (lower.contains(allowed)) return null;
        }
        for (String needle : BLACKLIST_CHANNEL_PATTERNS) {
            if (lower.contains(needle)) return new MatchResult(Severity.CHEAT, needle);
        }
        for (String needle : GREY_CHANNEL_PATTERNS) {
            if (lower.contains(needle)) return new MatchResult(Severity.GREY, needle);
        }
        return null;
    }

    public static MatchResult matchBrand(String brand) {
        if (brand == null) return null;
        String lower = brand.toLowerCase(Locale.ROOT);
        for (String needle : BLACKLIST_BRAND_PATTERNS) {
            if (lower.contains(needle)) return new MatchResult(Severity.CHEAT, needle);
        }
        return null;
    }

    /** True si l'un des channels du joueur est dans l'allowlist (ex. litematica). */
    public static boolean hasAllowedChannel(java.util.Collection<String> registeredChannels) {
        if (registeredChannels == null || registeredChannels.isEmpty()) return false;
        for (String ch : registeredChannels) {
            String lower = ch.toLowerCase(Locale.ROOT);
            for (String allowed : ALLOWED_CHANNEL_PATTERNS) {
                if (lower.contains(allowed)) return true;
            }
        }
        return false;
    }

    public enum Severity {
        /** Cheat client avéré (meteor, wurst, etc.). Action: bloquer transfert/kick. */
        CHEAT,
        /** Mod "gris" — ESP/minimap/WDL/freecam. Action: bloquer transfert par défaut. */
        GREY
    }

    public record MatchResult(Severity severity, String needle) {
        public String describe() {
            return severity.name().toLowerCase(Locale.ROOT) + ":" + needle;
        }
    }

    public static String[] brandEcosystemsArray() {
        return KNOWN_BRAND_ECOSYSTEMS.toArray(new String[0]);
    }

    public static List<String> allPatterns() {
        return Arrays.asList(String.join(",", BLACKLIST_CHANNEL_PATTERNS),
                String.join(",", BLACKLIST_BRAND_PATTERNS),
                String.join(",", GREY_CHANNEL_PATTERNS));
    }
}
