package fr.smp.core.enchants;

import fr.smp.core.SMPCore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central helpers for the custom-enchant system.
 *
 * Storage model
 * -------------
 *  - Custom enchants live in a single PDC string on the item:
 *      key `smpcore:ce` → "filonage:1,vitalite:3"
 *  - Over-cap enchants are stored as REAL vanilla enchants on the item
 *    (applied with ignoreLevelRestriction=true). Vanilla does the effect.
 *  - An "enchant book" is an ENCHANTED_BOOK with PDC `smpcore:ce_book` → "id:level".
 *    For OVERCAP books, the vanilla stored-enchant is also set at the target
 *    level so the anvil preview renders nicely.
 *
 * Lore is re-rendered on every write so it stays consistent with the PDC.
 */
public final class EnchantEngine {

    public static final NamespacedKey KEY_CE       = new NamespacedKey("smpcore", "ce");
    public static final NamespacedKey KEY_CE_BOOK  = new NamespacedKey("smpcore", "ce_book");
    public static final NamespacedKey KEY_LORE_LEN = new NamespacedKey("smpcore", "ce_lore_len");
    /** Marks a chestplate that has absorbed an elytra (for lore + anvil detection). */
    public static final NamespacedKey KEY_GLIDER   = new NamespacedKey("smpcore", "glider_chest");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private EnchantEngine() {}

    // ═══ Reading ════════════════════════════════════════════════════════

    /** Custom enchants currently on the item (empty if none or null). */
    public static Map<CustomEnchant, Integer> customOn(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptyMap();
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY_CE, PersistentDataType.STRING);
        return decode(raw);
    }

    public static int levelOf(ItemStack item, CustomEnchant ce) {
        if (item == null || ce == null) return 0;
        if (ce.isOvercap()) {
            if (!item.hasItemMeta()) return 0;
            int lvl = item.getItemMeta().getEnchantLevel(ce.vanilla());
            return lvl >= ce.maxLevel() ? lvl : 0;
        }
        return customOn(item).getOrDefault(ce, 0);
    }

    /** If this stack is an "enchant book" payload, returns (enchant, level). */
    public static record BookPayload(CustomEnchant enchant, int level) {}

    public static BookPayload readBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return null;
        if (!item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY_CE_BOOK, PersistentDataType.STRING);
        if (raw == null) return null;
        int i = raw.indexOf(':');
        if (i <= 0) return null;
        var opt = CustomEnchant.byId(raw.substring(0, i));
        if (opt.isEmpty()) return null;
        int level;
        try { level = Integer.parseInt(raw.substring(i + 1)); }
        catch (NumberFormatException nfe) { return null; }
        level = Math.max(1, Math.min(opt.get().maxLevel(), level));
        return new BookPayload(opt.get(), level);
    }

    // ═══ Writing ════════════════════════════════════════════════════════

    /** Adds / upgrades a custom enchant on the item. Re-renders lore. */
    public static void apply(ItemStack item, CustomEnchant ce, int level) {
        if (item == null || ce == null || level < 1) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        level = Math.max(1, Math.min(ce.maxLevel(), level));

        if (ce.isOvercap()) {
            // Paper 1.21+: ItemMeta.addEnchant(unsafe=true) is clamped at some
            // codec/validation layers (notably the anvil result slot in Paper
            // 26.x), which silently downgrades Fortune IV → III, Eff VI → V,
            // etc. Writing the ENCHANTMENTS data component directly bypasses
            // that clamping — the data component codec accepts any int level.
            setVanillaEnchantUnsafe(item, ce.vanilla(), level);
            renderLore(item);
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<CustomEnchant, Integer> cur = decode(pdc.get(KEY_CE, PersistentDataType.STRING));
        cur.merge(ce, level, Math::max);
        pdc.set(KEY_CE, PersistentDataType.STRING, encode(cur));
        item.setItemMeta(meta);
        renderLore(item);
    }

    public static void removeAll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(KEY_CE);
        item.setItemMeta(meta);
        renderLore(item);
    }

    // ═══ Books ══════════════════════════════════════════════════════════

    public static ItemStack book(CustomEnchant ce, int level) {
        level = Math.max(1, Math.min(ce.maxLevel(), level));
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta == null) return book;

        meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);

        meta.displayName(MM.deserialize(
                "<!italic><gradient:#ffd700:#ff8a00><bold>✦ " + ce.displayName() + " " +
                        CustomEnchant.roman(level) + "</bold></gradient>"));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic>" + ce.loreLine()));
        if (ce == CustomEnchant.QUARRY || ce == CustomEnchant.EXCAV || ce == CustomEnchant.HARVEST) {
            int side = bigRadius(level) * 2 + 1;
            lore.add(MM.deserialize("<!italic><gray>Zone: <aqua>" + side + "x" + side + "</aqua>.</gray>"));
        }
        if (ce == CustomEnchant.VITAL) {
            lore.add(MM.deserialize("<!italic><gray>Bonus: <red>+" + level + " ♥</red> d'absorption.</gray>"));
        }
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic><dark_gray>Applique via enclume sur un objet compatible.</dark_gray>"));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(
                KEY_CE_BOOK, PersistentDataType.STRING, ce.id() + ":" + level);

        book.setItemMeta(meta);
        // Overcap books: write the stored enchant via the data component
        // directly (the EnchantmentStorageMeta API clamps through the codec
        // in Paper 26.x, stripping overcap levels). The data-component codec
        // accepts any int level. The vanilla anvil will then transfer this
        // raw level straight onto the tool.
        if (ce.isOvercap()) {
            book.setData(DataComponentTypes.STORED_ENCHANTMENTS,
                    ItemEnchantments.itemEnchantments().add(ce.vanilla(), level).build());
        }
        // Custom books carry no stored enchant, so give them a glint via the
        // dedicated data component. Overcap books already glow from their
        // stored vanilla enchant.
        if (ce.isCustom()) {
            book.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return book;
    }

    /**
     * Sets a vanilla enchant at an arbitrary level on the item — including
     * levels above {@code Enchantment#getMaxLevel()} — by writing the
     * {@link DataComponentTypes#ENCHANTMENTS} component directly. Preserves
     * any other enchants already on the item.
     */
    public static void setVanillaEnchantUnsafe(ItemStack item, Enchantment ench, int level) {
        if (item == null || ench == null || level < 1) return;
        ItemEnchantments current = item.getData(DataComponentTypes.ENCHANTMENTS);
        ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
        if (current != null) {
            for (var e : current.enchantments().entrySet()) {
                if (!e.getKey().equals(ench)) {
                    builder.add(e.getKey(), e.getValue());
                }
            }
        }
        builder.add(ench, level);
        item.setData(DataComponentTypes.ENCHANTMENTS, builder.build());
    }

    // ═══ Anvil merge ════════════════════════════════════════════════════

    /** Merges the book's payload onto target. Returns null if incompatible. */
    public static ItemStack mergeBookOnto(ItemStack target, ItemStack book) {
        if (target == null || target.getType() == Material.AIR) return null;
        BookPayload payload = readBook(book);
        if (payload == null) return null;
        if (!payload.enchant().target().matches(target.getType())) return null;
        if (payload.level() <= appliedLevelOn(target, payload.enchant())) return null;

        ItemStack out = target.clone();

        // Defensive: covers legacy custom books still carrying the old
        // Unbreaking-1 dummy glow (pre-glint-override). Strip any vanilla
        // enchant that exists on `out` only because it was a storedEnchant
        // on the book, while preserving enchants the target already had.
        if (book.getItemMeta() instanceof EnchantmentStorageMeta esm
                && !esm.getStoredEnchants().isEmpty()) {
            ItemMeta om = out.getItemMeta();
            ItemMeta tm = target.getItemMeta();
            if (om != null) {
                boolean changed = false;
                for (Enchantment ench : esm.getStoredEnchants().keySet()) {
                    int origLvl = (tm == null) ? 0 : tm.getEnchantLevel(ench);
                    if (om.getEnchantLevel(ench) != origLvl) {
                        if (origLvl == 0) om.removeEnchant(ench);
                        else om.addEnchant(ench, origLvl, true);
                        changed = true;
                    }
                }
                if (changed) out.setItemMeta(om);
            }
        }

        apply(out, payload.enchant(), payload.level());
        return out;
    }

    private static int appliedLevelOn(ItemStack item, CustomEnchant ce) {
        if (item == null || ce == null) return 0;
        if (ce.isOvercap()) {
            if (!item.hasItemMeta()) return 0;
            return item.getItemMeta().getEnchantLevel(ce.vanilla());
        }
        return levelOf(item, ce);
    }

    /**
     * Fuses an elytra onto a chestplate. The output is the chestplate (so it
     * renders as proper armor on the player body) with the {@code minecraft:glider}
     * component set — the player still glides, boosts with fireworks, takes fall
     * mitigation, etc, but visually wears their diamond/netherite plate.
     *
     * Vanilla and custom enchants from both items are merged onto the output.
     */
    public static ItemStack fuseElytraOntoChestplate(ItemStack chest, ItemStack elytra) {
        if (chest == null || elytra == null) return null;
        if (elytra.getType() != Material.ELYTRA) return null;
        if (!chest.getType().name().endsWith("_CHESTPLATE")) return null;

        ItemStack out = chest.clone();
        ItemMeta om = out.getItemMeta();
        ItemMeta em = elytra.getItemMeta();
        if (om == null || em == null) return null;

        // Merge vanilla enchants (highest level wins, unsafe levels allowed).
        for (var e : em.getEnchants().entrySet()) {
            int cur = om.getEnchantLevel(e.getKey());
            if (e.getValue() > cur) om.addEnchant(e.getKey(), e.getValue(), true);
        }

        // Merge custom PDC enchants from elytra onto chestplate.
        String rawCe = em.getPersistentDataContainer().get(KEY_CE, PersistentDataType.STRING);
        if (rawCe != null) {
            Map<CustomEnchant, Integer> existing = decode(om.getPersistentDataContainer()
                    .get(KEY_CE, PersistentDataType.STRING));
            Map<CustomEnchant, Integer> incoming = decode(rawCe);
            for (var e : incoming.entrySet()) existing.merge(e.getKey(), e.getValue(), Math::max);
            om.getPersistentDataContainer().set(KEY_CE, PersistentDataType.STRING, encode(existing));
        }

        // Tag the chestplate so we can render a dedicated lore badge.
        om.getPersistentDataContainer().set(KEY_GLIDER, PersistentDataType.BYTE, (byte) 1);

        // The more damaged piece wins — so "flying down" the elytra carries over.
        if (om instanceof Damageable od && em instanceof Damageable ed) {
            od.setDamage(Math.max(od.getDamage(), ed.getDamage()));
        }

        out.setItemMeta(om);
        // The GLIDER data component lives on the ItemStack, not the ItemMeta.
        out.setData(DataComponentTypes.GLIDER);
        renderLore(out);
        return out;
    }

    // ═══ Lore rendering ═════════════════════════════════════════════════

    public static void renderLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<CustomEnchant, Integer> customs = decode(pdc.get(KEY_CE, PersistentDataType.STRING));
        Map<Enchantment, Integer> vanilla = meta.getEnchants();
        boolean hasVisibleEnchants = !vanilla.isEmpty() || !customs.isEmpty();
        boolean needsCustomGlint = vanilla.isEmpty() && !customs.isEmpty();

        Integer prevLen = pdc.get(KEY_LORE_LEN, PersistentDataType.INTEGER);
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (prevLen != null) {
            int drop = Math.min(prevLen, lore.size());
            lore.subList(0, drop).clear();
        }

        List<Component> inject = new ArrayList<>();

        // 0. Elytra-fused chestplate badge (shown above enchants).
        boolean isGliderChest = pdc.has(KEY_GLIDER, PersistentDataType.BYTE);
        if (isGliderChest) {
            inject.add(MM.deserialize(
                    "<!italic><gradient:#ffd700:#ff8a00><bold>✦ Mobilité Aérienne</bold></gradient>"));
        }

        // 1. Overcap vanilla enchants (Eff VI, Sharp VI, ...) — styled like customs.
        Map<Enchantment, CustomEnchant> overcapByVanilla = new HashMap<>();
        for (CustomEnchant ce : CustomEnchant.values()) {
            if (ce.isOvercap()) overcapByVanilla.put(ce.vanilla(), ce);
        }
        for (var e : vanilla.entrySet()) {
            CustomEnchant ce = overcapByVanilla.get(e.getKey());
            if (ce != null && e.getValue() >= ce.maxLevel()) {
                inject.add(MM.deserialize(
                        "<!italic><gradient:#67e8f9:#a78bfa>" + ce.displayName() + "</gradient> " +
                                "<light_purple>" + CustomEnchant.roman(e.getValue()) + "</light_purple>"));
            }
        }
        // 2. Regular vanilla enchants (Unb III, Eff V, ...) — localized name in gray.
        for (var e : vanilla.entrySet()) {
            CustomEnchant ce = overcapByVanilla.get(e.getKey());
            if (ce != null && e.getValue() >= ce.maxLevel()) continue; // already rendered as overcap
            NamespacedKey k = e.getKey().getKey();
            Component name = Component.translatable("enchantment." + k.getNamespace() + "." + k.getKey());
            inject.add(name.decoration(TextDecoration.ITALIC, false)
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(" " + CustomEnchant.roman(e.getValue()))));
        }
        // 3. Custom PDC enchants (Filonage, Carrière, Vitalité, ...) — styled gradient.
        for (var e : customs.entrySet()) {
            inject.add(MM.deserialize(
                    "<!italic><gradient:#67e8f9:#a78bfa>" + e.getKey().displayName() + "</gradient> " +
                            "<light_purple>" + CustomEnchant.roman(e.getValue()) + "</light_purple>"));
        }

        if (!inject.isEmpty()) {
            inject.add(MM.deserialize("<!italic> "));
            pdc.set(KEY_LORE_LEN, PersistentDataType.INTEGER, inject.size());
            lore.addAll(0, inject);
        } else {
            pdc.remove(KEY_LORE_LEN);
        }
        meta.lore(lore);
        if (hasVisibleEnchants) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        if (needsCustomGlint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            item.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    private static void stripLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer prevLen = pdc.get(KEY_LORE_LEN, PersistentDataType.INTEGER);
        if (prevLen != null && meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            int drop = Math.min(prevLen, lore.size());
            lore.subList(0, drop).clear();
            meta.lore(lore);
        }
        pdc.remove(KEY_LORE_LEN);
        item.setItemMeta(meta);
    }

    // ═══ Encoding ═══════════════════════════════════════════════════════

    private static Map<CustomEnchant, Integer> decode(String raw) {
        Map<CustomEnchant, Integer> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split(",")) {
            int i = part.indexOf(':');
            if (i <= 0) continue;
            CustomEnchant.byId(part.substring(0, i)).ifPresent(ce -> {
                try {
                    int lvl = Integer.parseInt(part.substring(i + 1));
                    if (lvl > 0) out.put(ce, Math.min(ce.maxLevel(), lvl));
                } catch (NumberFormatException ignored) {}
            });
        }
        return out;
    }

    private static String encode(Map<CustomEnchant, Integer> m) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey().id()).append(':').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    /** Size of the BigDig square at a given level (radius from center). */
    public static int bigRadius(int level) {
        return switch (level) {
            case 1 -> 1; // 3x3
            case 2 -> 2; // 5x5... user asked 4x4, we honor that via asymmetric break in listener
            case 3 -> 2; // 5x5
            default -> 1;
        };
    }

    /** Side length of the square (3, 4, 5) for BigDig at given level. */
    public static int bigSide(int level) {
        return switch (level) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 5;
            default -> 3;
        };
    }

    // ═══ Chance helpers (used by Table + MobDrop) ═══════════════════════

    public static CustomEnchant rollCustom(java.util.Random r, CustomEnchant.Target targetFilter) {
        List<CustomEnchant> pool = new ArrayList<>();
        for (CustomEnchant ce : CustomEnchant.values()) {
            if (targetFilter == null || ce.target() == targetFilter || ce.target() == CustomEnchant.Target.BREAKABLE) {
                pool.add(ce);
            }
        }
        if (pool.isEmpty()) return null;
        return pool.get(r.nextInt(pool.size()));
    }
}
