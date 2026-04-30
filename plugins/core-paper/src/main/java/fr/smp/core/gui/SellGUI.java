package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.commands.SellCommand;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.sell.SellCategory;
import fr.smp.core.sell.SellTierManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sell drop chest with snowball tier multipliers.
 *
 * Layout (54 slots, 6 rows):
 *   <pre>
 *   rows 0..4 (slots 0..44)  — drop area, players freely place items here
 *   row 5    (slots 45..53) — 9 category buttons (one per {@link SellCategory})
 *   </pre>
 * On close, each item present is sold at {@code base × tierMultiplier(category)},
 * where the multiplier depends on how many items the player has cumulatively
 * sold in that category. The progression is shown by clicking a category button,
 * which opens {@link TierCategoryGUI}.
 */
public class SellGUI extends GUIHolder {

    /** Number of "free" slots reserved for dropping items. Bottom row stays for buttons. */
    public static final int DROP_SLOTS = 45;

    private final SMPCore plugin;
    /** When the player clicks a category button, we transition to TierCategoryGUI;
     *  in that case onClose must NOT process the items still in the GUI. */
    private boolean transitioning;

    public SellGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f6d365:#fda085><bold>Sell</bold></gradient>"));
        this.inventory = inv;
        renderCategoryRow(p);
        p.openInventory(inv);
        plugin.getLogger().info("[SELL] " + p.getName() + " a ouvert le GUI sell");
        p.sendMessage(Msg.info("<gray>Dépose les items dans le coffre. Ferme pour vendre. Clique une catégorie en bas pour voir tes paliers.</gray>"));
    }

    /** Build the bottom row with one button per category, lore showing current tier + multiplier. */
    private void renderCategoryRow(Player p) {
        PlayerData data = plugin.players().get(p.getUniqueId());
        SellCategory[] cats = SellCategory.values();
        for (int i = 0; i < cats.length; i++) {
            SellCategory cat = cats[i];
            long count = data == null ? 0 : data.tierSellCount(i);
            int tier = SellTierManager.tierFor(count);
            double mult = SellTierManager.MULTIPLIERS[tier];
            long next = SellTierManager.nextThreshold(tier);
            String[] lore;
            if (next < 0) {
                lore = new String[]{
                        "<gray>Palier <yellow>T" + tier + " / T" + SellTierManager.MAX_TIER + "</yellow>",
                        "<gray>Multiplicateur <green>x" + fmtMult(mult) + "</green>",
                        "<gray>Vendus: <white>" + fmtCount(count) + "</white>",
                        "",
                        "<gold>★ Palier maximum atteint ★",
                        "",
                        "<aqua>▶ Clique pour les détails</aqua>"
                };
            } else {
                long left = Math.max(0, next - count);
                double nextMult = SellTierManager.nextMultiplier(tier);
                lore = new String[]{
                        "<gray>Palier <yellow>T" + tier + " / T" + SellTierManager.MAX_TIER + "</yellow>",
                        "<gray>Multiplicateur <green>x" + fmtMult(mult) + "</green>",
                        "<gray>Vendus: <white>" + fmtCount(count) + "</white>",
                        "",
                        "<gray>Prochain: <yellow>T" + (tier + 1) + "</yellow> <dark_gray>→</dark_gray> <green>x" + fmtMult(nextMult) + "</green>",
                        "<gray>Manque: <yellow>" + fmtCount(left) + "</yellow>",
                        "",
                        "<aqua>▶ Clique pour les détails</aqua>"
                };
            }
            inventory.setItem(DROP_SLOTS + i, GUIUtil.item(cat.icon(),
                    cat.colorTag() + "<bold>" + cat.displayName() + "</bold>",
                    lore));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (inventory == null) return;
        int raw = event.getRawSlot();
        int topSize = inventory.getSize();

        // Block double-click "collect to cursor": Bukkit would scan both
        // inventories and pull matching items together — that includes the
        // category buttons in the bottom row. We just disable the QoL.
        if (event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        // Click outside any inventory window (-999): drop on floor — let it through.
        if (raw < 0) {
            event.setCancelled(false);
            return;
        }

        // Click in player's own inventory.
        if (raw >= topSize) {
            if (event.isShiftClick()) {
                // Shift-click would have Bukkit auto-move the item into the
                // top inventory, potentially into our reserved button row.
                // We re-implement it by placing into slots 0..DROP_SLOTS-1 only.
                ItemStack moved = event.getCurrentItem();
                if (moved == null || moved.getType().isAir()) return;
                event.setCancelled(true);
                ItemStack remaining = moved.clone();
                int origAmount = remaining.getAmount();
                // Phase 1: stack on existing similar stacks in drop area.
                for (int i = 0; i < DROP_SLOTS && remaining.getAmount() > 0; i++) {
                    ItemStack here = inventory.getItem(i);
                    if (here == null || here.getType().isAir()) continue;
                    if (!here.isSimilar(remaining)) continue;
                    int max = here.getMaxStackSize();
                    int free = max - here.getAmount();
                    if (free <= 0) continue;
                    int take = Math.min(free, remaining.getAmount());
                    here.setAmount(here.getAmount() + take);
                    remaining.setAmount(remaining.getAmount() - take);
                }
                // Phase 2: drop into empty slots in drop area.
                for (int i = 0; i < DROP_SLOTS && remaining.getAmount() > 0; i++) {
                    ItemStack here = inventory.getItem(i);
                    if (here != null && !here.getType().isAir()) continue;
                    int take = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
                    ItemStack drop = remaining.clone();
                    drop.setAmount(take);
                    inventory.setItem(i, drop);
                    remaining.setAmount(remaining.getAmount() - take);
                }
                int placed = origAmount - remaining.getAmount();
                if (placed <= 0) return;
                if (remaining.getAmount() <= 0) {
                    event.setCurrentItem(null);
                } else {
                    moved.setAmount(remaining.getAmount());
                    event.setCurrentItem(moved);
                }
                return;
            }
            // Regular click in player inventory: leave it alone.
            event.setCancelled(false);
            return;
        }

        // Click in our top inventory.
        if (raw < DROP_SLOTS) {
            // Drop area — let the player freely place items.
            event.setCancelled(false);
            return;
        }

        // Bottom row (slots 45..53) — category buttons.
        event.setCancelled(true);
        int catIdx = raw - DROP_SLOTS;
        SellCategory[] cats = SellCategory.values();
        if (catIdx < 0 || catIdx >= cats.length) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;

        // Mark transitioning so onClose doesn't process the dropped items
        // (player isn't actually leaving the sell flow — they'll come back
        // when they exit the tier GUI). We schedule the open one tick later
        // because Bukkit forbids InventoryAction during a click event.
        transitioning = true;
        Bukkit.getScheduler().runTask(plugin, () -> new TierCategoryGUI(plugin, cats[catIdx]).open(p));
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        if (inventory == null) return;
        Set<Integer> raws = event.getRawSlots();
        int topSize = inventory.getSize();
        // Only allow drags whose every dragged slot is either a drop slot of
        // this GUI, or a slot of the player's own inventory. Reject any drag
        // that touches the bottom button row.
        for (int raw : raws) {
            if (raw < topSize && raw >= DROP_SLOTS) {
                return; // keep it cancelled
            }
        }
        event.setCancelled(false);
    }

    @Override
    public void onClose(HumanEntity who) {
        if (!(who instanceof Player p) || inventory == null) return;
        if (transitioning) {
            // The player clicked a category button; we'll open another GUI
            // next tick. We must NOT swallow the items they had dropped in.
            // Re-open the same SellGUI (with same contents) is messy, so the
            // simpler contract: return everything to the player so they can
            // start over. We then reset the flag on this instance.
            returnAll(p);
            transitioning = false;
            return;
        }

        double total = 0;
        int items = 0;
        int returned = 0;
        Map<Material, double[]> breakdown = new LinkedHashMap<>();
        Map<Material, Integer> returnedBreakdown = new LinkedHashMap<>();
        // Per-category aggregation so we record progress + announce level-ups
        // once per category (instead of spamming chat on every stack).
        long[] catItems = new long[SellCategory.values().length];
        double[] catValue = new double[SellCategory.values().length];
        Material[] catSampleMat = new Material[SellCategory.values().length];

        PlayerData data = plugin.players().get(p.getUniqueId());

        for (int i = 0; i < DROP_SLOTS; i++) {
            ItemStack s = inventory.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            double base = plugin.worth().worth(s);
            if (base <= 0) {
                var overflow = p.getInventory().addItem(s.clone());
                overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                returned += s.getAmount();
                returnedBreakdown.merge(s.getType(), s.getAmount(), Integer::sum);
                inventory.setItem(i, null);
                continue;
            }
            SellCategory cat = SellCategory.of(s.getType());
            double mult = (cat != null && data != null)
                    ? plugin.sellTiers().multiplier(data, cat)
                    : 1.0;
            double v = base * mult;
            total += v;
            items += s.getAmount();
            SellCommand.accumulate(breakdown, s.getType(), s.getAmount(), v);
            if (cat != null) {
                int idx = cat.ordinal();
                catItems[idx] += s.getAmount();
                catValue[idx] += v;
                if (catSampleMat[idx] == null) catSampleMat[idx] = s.getType();
            }
            inventory.setItem(i, null);
        }

        if (total > 0) {
            double before = plugin.economy().balance(p.getUniqueId());
            plugin.economy().deposit(p.getUniqueId(), total, "sell.gui");
            double after = plugin.economy().balance(p.getUniqueId());
            // Record progression + collect any tier-up announcements.
            if (data != null) {
                for (int idx = 0; idx < catItems.length; idx++) {
                    if (catItems[idx] <= 0) continue;
                    boolean levelUp = plugin.sellTiers().recordSale(p.getUniqueId(),
                            catSampleMat[idx], (int) Math.min(catItems[idx], Integer.MAX_VALUE), catValue[idx]);
                    if (levelUp) {
                        SellCategory cat = SellCategory.values()[idx];
                        int newTier = SellTierManager.tierFor(data.tierSellCount(idx));
                        double newMult = SellTierManager.MULTIPLIERS[newTier];
                        p.sendMessage(Msg.ok("<gold>★ Palier débloqué — <yellow>"
                                + cat.displayName() + " T" + newTier
                                + "</yellow> <gray>(<green>x" + fmtMult(newMult) + "</green>)</gray>"));
                    }
                }
            }
            p.sendMessage(Msg.ok("<green>Vendu <yellow>×" + items + "</yellow> pour <yellow>$" +
                    Msg.money(total) + "</yellow>.</green>"));
            String log = SellCommand.formatLog("gui", items, total, before, after, breakdown);
            if (returned > 0) {
                StringBuilder rb = new StringBuilder();
                returnedBreakdown.forEach((m, c) -> {
                    if (rb.length() > 0) rb.append(" | ");
                    rb.append(m.name()).append(" x").append(c);
                });
                log += " returned=" + returned + " returnedBreakdown={" + rb + "}";
            }
            plugin.logs().log(LogCategory.SELL, p, log);
            plugin.getLogger().info("[SELL] " + p.getName() + " " + log);
            plugin.getSyncManager().markDirty(p);
        } else if (returned == 0) {
            p.sendMessage(Msg.info("<gray>Rien à vendre.</gray>"));
        }
        if (returned > 0) {
            p.sendMessage(Msg.info("<yellow>" + returned + " items sans valeur retournés.</yellow>"));
        }
    }

    /**
     * Return every dropped item from the drop area (slots 0..DROP_SLOTS-1) to
     * the player. Used when the player navigates to the tier-detail GUI mid-sell.
     */
    private void returnAll(Player p) {
        for (int i = 0; i < DROP_SLOTS; i++) {
            ItemStack s = inventory.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            var overflow = p.getInventory().addItem(s.clone());
            overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            inventory.setItem(i, null);
        }
    }

    private static String fmtMult(double m) {
        if (m == Math.floor(m)) return String.format("%.0f", m);
        return String.format("%.2f", m);
    }

    private static String fmtCount(long n) {
        if (n >= 1_000_000_000L) return String.format("%.2fB", n / 1_000_000_000.0);
        if (n >= 1_000_000L)     return String.format("%.2fM", n / 1_000_000.0);
        if (n >= 10_000L)        return String.format("%.1fK", n / 1_000.0);
        if (n >= 1_000L)         return String.format("%.2fK", n / 1_000.0);
        return Long.toString(n);
    }
}
