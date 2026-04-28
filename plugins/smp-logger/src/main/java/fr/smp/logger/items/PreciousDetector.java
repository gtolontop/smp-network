package fr.smp.logger.items;

import fr.smp.logger.SMPLogger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether an item is "precious" (worth dedup-storing the full NBT)
 * or "trivial" (just material id + amount is enough).
 *
 * Always-precious set comes from config (default: books, totems, shulkers, élytres,
 * netherite gear, music discs, beacons, tridents, spawners, vaults, custom heads...).
 * Auto-detect catches everything with a custom name, lore, custom enchant or PDC.
 */
public class PreciousDetector {

    private final SMPLogger plugin;
    private final Set<Material> alwaysPrecious;
    private final boolean detectNamed;
    private final boolean detectLored;
    private final boolean detectNonVanillaEnchants;
    private final boolean detectBlockEntity;

    public PreciousDetector(SMPLogger plugin) {
        this.plugin = plugin;
        this.alwaysPrecious = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("precious.always")) {
            try { alwaysPrecious.add(Material.valueOf(s)); }
            catch (IllegalArgumentException ignored) {}
        }
        this.detectNamed = plugin.getConfig().getBoolean("precious.auto-detect.named", true);
        this.detectLored = plugin.getConfig().getBoolean("precious.auto-detect.lored", true);
        this.detectNonVanillaEnchants = plugin.getConfig().getBoolean("precious.auto-detect.enchanted-non-vanilla", true);
        this.detectBlockEntity = plugin.getConfig().getBoolean("precious.auto-detect.has-block-entity-tag", true);
    }

    public boolean isPrecious(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (alwaysPrecious.contains(item.getType())) return true;

        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        if (detectNamed && meta.hasDisplayName()) return true;
        if (detectLored && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) return true;
        }
        if (detectNonVanillaEnchants && meta.hasEnchants()) {
            for (Enchantment e : meta.getEnchants().keySet()) {
                NamespacedKey key = e.getKey();
                if (!"minecraft".equals(key.getNamespace())) return true;
            }
        }
        if (detectBlockEntity) {
            // Heuristic: any custom PDC entry on a stored item flags it.
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!pdc.getKeys().isEmpty()) return true;
        }
        return false;
    }

    /** Short human summary stored alongside the full NBT for /lookup output. */
    public String summarize(ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        StringBuilder sb = new StringBuilder();
        sb.append(item.getType().name());
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name != null && !name.isEmpty()) sb.append(" \"").append(stripFormatting(name)).append('"');
        }
        if (meta != null && meta.hasEnchants()) {
            sb.append(" {");
            int n = 0;
            for (var e : meta.getEnchants().entrySet()) {
                if (n++ > 0) sb.append(',');
                sb.append(e.getKey().getKey().getKey()).append(':').append(e.getValue());
            }
            sb.append('}');
        }
        if (sb.length() > 200) sb.setLength(200);
        return sb.toString();
    }

    private static String stripFormatting(String s) {
        return s.replaceAll("§.", "").replaceAll("<[^>]+>", "");
    }
}
