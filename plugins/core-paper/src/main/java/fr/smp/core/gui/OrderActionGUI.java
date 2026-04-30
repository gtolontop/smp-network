package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.order.Order;
import fr.smp.core.order.OrderManager;
import fr.smp.core.order.OrderSort;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 27-slot detail/action GUI for a single order.
 *
 * <ul>
 *   <li>For the buyer (cancelMode=true): shows refund amount + cancel button.</li>
 *   <li>For a seller (cancelMode=false): shows what they have in inventory + deliver button
 *       (which opens a sign prompt for quantity).</li>
 * </ul>
 *
 * Layout (3 rows):
 * <pre>
 *   row 0: [filler × 9]
 *   row 1: [f] [ORDER_INFO:10] [f] [f] [ACTION:13] [f] [f] [BACK:16] [f]
 *   row 2: [filler × 9]
 * </pre>
 */
public class OrderActionGUI extends GUIHolder {

    private static final int SLOT_INFO   = 10;
    private static final int SLOT_ACTION = 13;
    private static final int SLOT_BACK   = 16;

    private final SMPCore plugin;
    private final Order order;
    private final boolean cancelMode;
    private boolean transitioning = false;

    public OrderActionGUI(SMPCore plugin, Order order, boolean cancelMode) {
        this.plugin     = plugin;
        this.order      = order;
        this.cancelMode = cancelMode;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void open(Player p) {
        String title = cancelMode
                ? "<gradient:#f093fb:#f5576c>Annuler commande</gradient>"
                : "<gradient:#43e97b:#38f9d7>Livrer</gradient>";
        Inventory inv = Bukkit.createInventory(this, 27, GUIUtil.title(title));
        this.inventory = inv;
        render(p);
        p.openInventory(inv);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private void render(Player viewer) {
        inventory.clear();
        for (int i = 0; i < 27; i++) inventory.setItem(i, GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE));

        // Order info card
        inventory.setItem(SLOT_INFO, GUIUtil.item(order.getItemType(),
                "<white>" + OrderManager.prettyMat(order.getItemType()),
                "<gray>De: <white>" + order.getBuyerName(),
                "<gray>Restant: <white>" + order.getRemainingQuantity() + "<gray>/" + order.getQuantity(),
                "<gray>Prix/u: <gold>" + Msg.money(order.getPricePerUnit()) + "$",
                "<gray>Total restant: <gold>" + Msg.money(order.getRemainingValue()) + "$",
                "<gray>Commande <dark_gray>#" + order.getId()));

        // Action button
        if (cancelMode) {
            inventory.setItem(SLOT_ACTION, GUIUtil.item(Material.RED_STAINED_GLASS_PANE,
                    "<red><bold>Annuler la commande",
                    "",
                    "<gray>Remboursement: <green>" + Msg.money(order.getRemainingValue()) + "$",
                    "<dark_gray>(items déjà livrés ne sont pas remboursés)"));
        } else {
            int inInv = countInInv(viewer, order.getItemType());
            int canDeliver = Math.min(inInv, order.getRemainingQuantity());
            inventory.setItem(SLOT_ACTION, GUIUtil.item(Material.LIME_STAINED_GLASS_PANE,
                    "<green><bold>Livrer des items",
                    "",
                    "<gray>Tu as: <white>" + inInv + " " + OrderManager.prettyMat(order.getItemType()),
                    "<gray>Restant à livrer: <white>" + order.getRemainingQuantity(),
                    canDeliver > 0
                            ? "<gray>Gain max: <gold>" + Msg.money(canDeliver * order.getPricePerUnit()) + "$"
                            : "<red>Tu n'as pas cet item",
                    "",
                    "<gray>Cliquer → choisir la quantité via panneau"));
        }

        // Back button
        inventory.setItem(SLOT_BACK, GUIUtil.item(Material.ARROW,
                "<white>← Retour",
                "<gray>Retour à la liste"));
    }

    // ── Click ─────────────────────────────────────────────────────────────────

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                    new OrderBrowseGUI(plugin, OrderBrowseGUI.Filter.all(), OrderSort.DATE_DESC).open(player));
            return;
        }

        if (slot == SLOT_ACTION) {
            if (cancelMode) {
                // Reload from DB to ensure still active
                Order fresh = plugin.orders().getById(order.getId());
                if (fresh == null || fresh.isFullyFulfilled()) {
                    player.sendMessage(Msg.err("Cette commande n'existe plus ou est déjà complète."));
                    player.closeInventory();
                    return;
                }
                plugin.orders().cancel(player, fresh);
                player.closeInventory();
            } else {
                // Deliver: ask quantity via sign
                int maxDeliverable = Math.min(
                        countInInv(player, order.getItemType()),
                        order.getRemainingQuantity());
                if (maxDeliverable <= 0) {
                    player.sendMessage(Msg.err("Tu n'as pas de "
                            + OrderManager.prettyMat(order.getItemType()) + " dans l'inventaire."));
                    return;
                }
                transitioning = true;
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.signInput().open(player,
                                "Qte (max " + maxDeliverable + ")",
                                text -> {
                                    int qty;
                                    try {
                                        qty = Integer.parseInt(text.trim());
                                    } catch (NumberFormatException e) {
                                        player.sendMessage(Msg.err("Valeur non valide: \"" + text + "\""));
                                        new OrderActionGUI(plugin, order, false).open(player);
                                        return;
                                    }
                                    if (qty <= 0 || qty > maxDeliverable) {
                                        player.sendMessage(Msg.err("Quantité invalide (1-" + maxDeliverable + ")."));
                                        new OrderActionGUI(plugin, order, false).open(player);
                                        return;
                                    }
                                    // Reload order to get current state
                                    Order fresh = plugin.orders().getById(order.getId());
                                    if (fresh == null || fresh.isFullyFulfilled()) {
                                        player.sendMessage(Msg.err("Commande déjà complète ou annulée."));
                                        return;
                                    }
                                    plugin.orders().fulfill(player, fresh, qty);
                                },
                                () -> new OrderActionGUI(plugin, order, false).open(player)));
            }
        }
    }

    @Override
    public void onClose(HumanEntity who) {
        transitioning = false;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int countInInv(Player p, Material m) {
        int count = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m) count += it.getAmount();
        }
        return count;
    }
}
