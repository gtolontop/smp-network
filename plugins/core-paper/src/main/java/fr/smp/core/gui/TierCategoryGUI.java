package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.sell.SellCategory;
import fr.smp.core.sell.SellTierManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Tier-detail GUI for one {@link SellCategory}.
 *
 * <p>Layout (54 slots, 6 rows):
 * <pre>
 *   row 0: top border (light-gray glass)
 *   row 1: stat header — slot 4 = category icon with running totals
 *   row 2..3: 12 tier panes laid out in 6+6 — yellow when reached, gray when locked
 *   row 4: progress bar towards next tier
 *   row 5: bottom border + back button (slot 49)
 * </pre>
 *
 * <p>Pane colors follow the spec the user asked for:
 * <ul>
 *   <li>Yellow stained glass pane = tier already unlocked (multiplier active)</li>
 *   <li>Gray  stained glass pane = tier still locked</li>
 * </ul>
 */
public class TierCategoryGUI extends GUIHolder {

    private static final int[] TIER_SLOTS = {
            // T1 .. T6 across the middle row
            19, 20, 21, 22, 23, 24,
            // T7 .. T12 across the row below
            28, 29, 30, 31, 32, 33
    };

    /** Slot used to draw the next-tier progress bar (row 4, slots 37..43 — 7 cells). */
    private static final int[] PROGRESS_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    private static final int STAT_SLOT = 4;
    private static final int BACK_SLOT = 49;
    private static final int REOPEN_SELL_SLOT = 45;

    private final SMPCore plugin;
    private final SellCategory category;

    public TierCategoryGUI(SMPCore plugin, SellCategory category) {
        this.plugin = plugin;
        this.category = category;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title(category.colorTag() + "<bold>" + category.displayName() + "</bold>"));
        this.inventory = inv;
        render(p);
        p.openInventory(inv);
    }

    private void render(Player p) {
        // Backdrop: gray panes everywhere first, then we overlay actual content.
        ItemStack borderTop = paneGray("<dark_gray> </dark_gray>");
        ItemStack borderBottom = paneGray("<dark_gray> </dark_gray>");
        for (int c = 0; c < 9; c++) {
            inventory.setItem(c, borderTop);
            inventory.setItem(45 + c, borderBottom);
        }
        // Side rails on rows 1..4
        for (int r = 1; r <= 4; r++) {
            inventory.setItem(r * 9, borderTop);
            inventory.setItem(r * 9 + 8, borderTop);
        }
        // Inner empty slots are filled with light filler so the GUI looks tidy.
        for (int slot : new int[]{10, 11, 12, 13, 14, 15, 16,
                                  25, 26, 34, 35,
                                  36, 44}) {
            inventory.setItem(slot, GUIUtil.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        }

        PlayerData data = plugin.players().get(p.getUniqueId());
        long count = data == null ? 0 : data.tierSellCount(category.ordinal());
        double money = data == null ? 0 : data.tierMoneyEarned(category.ordinal());
        int currentTier = SellTierManager.tierFor(count);
        double currentMult = SellTierManager.MULTIPLIERS[currentTier];
        long nextThreshold = SellTierManager.nextThreshold(currentTier);
        double nextMult = SellTierManager.nextMultiplier(currentTier);

        renderStatHeader(count, money, currentTier, currentMult, nextThreshold, nextMult);
        renderTierPanes(count, currentTier);
        renderProgressBar(count, currentTier);

        // Bottom row controls
        inventory.setItem(REOPEN_SELL_SLOT, GUIUtil.item(Material.CHEST,
                "<#fda085><bold>Retour au /sell</bold>",
                "<gray>Rouvre le coffre de vente"));
        inventory.setItem(BACK_SLOT, GUIUtil.item(Material.BARRIER,
                "<red><bold>Fermer</bold>",
                "<gray>Ferme cette interface"));
    }

    private void renderStatHeader(long count, double money, int currentTier, double currentMult,
                                  long nextThreshold, double nextMult) {
        String[] lore;
        if (nextThreshold < 0) {
            lore = new String[]{
                    "",
                    "<gray>Palier actuel: <gold>T" + currentTier + " / T" + SellTierManager.MAX_TIER + "</gold>",
                    "<gray>Multiplicateur: <green>x" + fmtMult(currentMult) + "</green>",
                    "",
                    "<gray>Items vendus: <white>" + fmtCount(count) + "</white>",
                    "<gray>Total gagné: <yellow>$" + Msg.money(money) + "</yellow>",
                    "",
                    "<gold>★ Palier maximum atteint ★",
                    "<gray>Tu es légendaire dans cette catégorie."
            };
        } else {
            long left = Math.max(0, nextThreshold - count);
            int pct = (int) Math.min(99, (count - SellTierManager.THRESHOLDS[currentTier]) * 100
                    / Math.max(1, nextThreshold - SellTierManager.THRESHOLDS[currentTier]));
            lore = new String[]{
                    "",
                    "<gray>Palier actuel: <yellow>T" + currentTier + " / T" + SellTierManager.MAX_TIER + "</yellow>",
                    "<gray>Multiplicateur: <green>x" + fmtMult(currentMult) + "</green>",
                    "",
                    "<gray>Items vendus: <white>" + fmtCount(count) + "</white>",
                    "<gray>Total gagné: <yellow>$" + Msg.money(money) + "</yellow>",
                    "",
                    "<gray>Prochain palier: <yellow>T" + (currentTier + 1) + "</yellow> <dark_gray>→</dark_gray> <green>x" + fmtMult(nextMult) + "</green>",
                    "<gray>Manque: <yellow>" + fmtCount(left) + "</yellow> <gray>items <dark_gray>(" + pct + "%)</dark_gray>"
            };
        }
        ItemStack header = GUIUtil.item(category.icon(),
                category.colorTag() + "<bold>" + category.displayName() + "</bold>",
                lore);
        inventory.setItem(STAT_SLOT, header);
    }

    private void renderTierPanes(long count, int currentTier) {
        for (int t = 1; t <= SellTierManager.MAX_TIER; t++) {
            boolean reached = currentTier >= t;
            double mult = SellTierManager.MULTIPLIERS[t];
            long needed = SellTierManager.THRESHOLDS[t];
            String[] lore;
            if (reached) {
                lore = new String[]{
                        "<gray>Statut: <green>débloqué ✔</green>",
                        "<gray>Multiplicateur: <green>x" + fmtMult(mult) + "</green>",
                        "<gray>Seuil: <white>" + fmtCount(needed) + "</white>",
                        "",
                        "<dark_green>Ce palier est actif sur tes ventes."
                };
            } else {
                long left = Math.max(0, needed - count);
                lore = new String[]{
                        "<gray>Statut: <red>verrouillé ✘</red>",
                        "<gray>Multiplicateur: <yellow>x" + fmtMult(mult) + "</yellow>",
                        "<gray>Seuil: <white>" + fmtCount(needed) + "</white>",
                        "",
                        "<gray>Manque: <yellow>" + fmtCount(left) + "</yellow> <gray>items"
                };
            }
            Material mat = reached
                    ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            String name = (reached ? "<yellow>" : "<dark_gray>")
                    + "<bold>Palier T" + t + "</bold> "
                    + "<gray>— x" + fmtMult(mult);
            ItemStack pane = GUIUtil.item(mat, name, lore);
            // Stack count = tier level so the panes display "1, 2, 3 ..." in inventory UI.
            pane.setAmount(Math.min(t, 64));
            inventory.setItem(TIER_SLOTS[t - 1], pane);
        }
    }

    private void renderProgressBar(long count, int currentTier) {
        long base = SellTierManager.THRESHOLDS[currentTier];
        long target = (currentTier >= SellTierManager.MAX_TIER) ? base : SellTierManager.THRESHOLDS[currentTier + 1];
        long span = Math.max(1, target - base);
        long progressed = Math.max(0, count - base);
        int filled = (int) Math.min(PROGRESS_SLOTS.length, (progressed * PROGRESS_SLOTS.length) / span);
        if (currentTier >= SellTierManager.MAX_TIER) filled = PROGRESS_SLOTS.length;

        for (int i = 0; i < PROGRESS_SLOTS.length; i++) {
            boolean done = i < filled;
            Material mat = done ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            String label = done
                    ? "<green><bold>■</bold></green>"
                    : "<dark_gray><bold>□</bold></dark_gray>";
            ItemStack cell = GUIUtil.item(mat, label,
                    currentTier >= SellTierManager.MAX_TIER
                            ? new String[]{"<gold>Palier maximum atteint."}
                            : new String[]{
                                    "<gray>Progression vers <yellow>T" + (currentTier + 1) + "</yellow>",
                                    "<gray>" + fmtCount(progressed) + " <dark_gray>/</dark_gray> " + fmtCount(target - base)
                            });
            inventory.setItem(PROGRESS_SLOTS[i], cell);
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // Everything is read-only in this GUI; the parent listener already
        // cancels the event, so we just dispatch button clicks here.
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        if (raw == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) () -> p.closeInventory());
        } else if (raw == REOPEN_SELL_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> new SellGUI(plugin).open(p));
        }
    }

    private static ItemStack paneGray(String name) {
        return GUIUtil.item(Material.GRAY_STAINED_GLASS_PANE, name);
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
