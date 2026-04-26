package fr.smp.core.enchants;

import fr.smp.core.SMPCore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hooks into the grindstone to properly strip custom enchants and overcap
 * vanilla enchants when a player uses the grindstone.
 *
 * Without this listener the vanilla grindstone only removes real vanilla
 * enchants — the PDC-based custom enchants survive, and the custom lore
 * injected by {@link EnchantEngine#renderLore} becomes stale.
 */
public class GrindstoneListener implements Listener {

    private final SMPCore plugin;

    public GrindstoneListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    // ── Preview: update the result slot so the player sees the right item ──

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent e) {
        ItemStack result = e.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        // Check if any input item carries custom enchant data.  Even if the
        // vanilla result doesn't carry the PDC (some Paper builds rebuild the
        // result from scratch), we still need to clean stale lore / glint.
        boolean inputDirty = hasCustomData(e.getInventory().getItem(0))
                          || hasCustomData(e.getInventory().getItem(1));

        result = result.clone();
        boolean changed = cleanItem(result);

        // If the inputs had custom data but cleanItem found nothing to strip
        // (vanilla rebuilt a fresh item), force a re-render so lore is clean.
        if (!changed && inputDirty) {
            EnchantEngine.renderLore(result);
            changed = true;
        }

        if (changed) {
            e.setResult(result);
            plugin.getLogger().fine("[Grindstone] Preview cleaned.");
        }
    }

    // ── Take: when the player clicks the result slot, apply cleanup again ──
    // The vanilla server may recalculate the result between PrepareGrindstone
    // and the actual click, so we must re-apply the cleanup here.

    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstoneTake(InventoryClickEvent e) {
        if (!(e.getInventory() instanceof GrindstoneInventory grind)) return;
        if (e.getRawSlot() != 2) return;              // result slot

        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        boolean inputDirty = hasCustomData(grind.getItem(0))
                          || hasCustomData(grind.getItem(1));

        boolean changed = cleanItem(result);
        if (!changed && inputDirty) {
            EnchantEngine.renderLore(result);
            changed = true;
        }

        if (changed) {
            e.setCurrentItem(result);
        }

        // Belt-and-suspenders: also clean the cursor item 1 tick later in case
        // vanilla overwrote our changes during click processing.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (e.getWhoClicked() == null) return;
            ItemStack cursor = e.getWhoClicked().getItemOnCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (cleanItem(cursor)) {
                    e.getWhoClicked().setItemOnCursor(cursor);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════

    /** Returns true if the item carries any custom enchant PDC data. */
    private boolean hasCustomData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(EnchantEngine.KEY_CE, PersistentDataType.STRING)
            || pdc.has(EnchantEngine.KEY_LORE_LEN, PersistentDataType.INTEGER);
    }

    /**
     * Strips custom PDC enchants, overcap vanilla levels, and the glint
     * override from the given item.  Re-renders lore to match.
     *
     * @return true if any modification was made.
     */
    private boolean cleanItem(ItemStack item) {
        boolean changed = false;

        // ── 1. Strip custom PDC enchants ────────────────────────────────
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            // Remove the custom enchant string (e.g. "filonage:1,vitalite:3").
            // NOTE: do NOT remove KEY_LORE_LEN here — renderLore needs it to
            // know how many stale lore lines to strip from the top of the
            // tooltip.  renderLore removes the key itself when nothing remains.
            if (pdc.has(EnchantEngine.KEY_CE, PersistentDataType.STRING)) {
                pdc.remove(EnchantEngine.KEY_CE);
                changed = true;
            }

            if (changed) {
                item.setItemMeta(meta);
            }
        }

        // ── 2. Strip overcap vanilla enchant levels ────────────────────
        ItemEnchantments enchData = item.getData(DataComponentTypes.ENCHANTMENTS);
        if (enchData != null && !enchData.enchantments().isEmpty()) {
            ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
            boolean overcapStripped = false;
            for (var entry : enchData.enchantments().entrySet()) {
                Enchantment ench = entry.getKey();
                int level = entry.getValue();
                if (level > ench.getMaxLevel()) {
                    if (ench.isCursed()) {
                        builder.add(ench, ench.getMaxLevel());
                    }
                    overcapStripped = true;
                } else {
                    builder.add(ench, level);
                }
            }
            if (overcapStripped) {
                item.setData(DataComponentTypes.ENCHANTMENTS, builder.build());
                changed = true;
            }
        }

        // ── 3. Remove the enchantment glint override if present ────────
        if (Boolean.TRUE.equals(item.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE))) {
            item.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
            changed = true;
        }

        // ── 4. Re-render lore so the display matches the new state ─────
        if (changed) {
            EnchantEngine.renderLore(item);
        }

        return changed;
    }
}
