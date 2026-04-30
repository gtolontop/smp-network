package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.order.OrderManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * 45-slot order creation form with 3 fields: item, quantity, price/unit.
 *
 * Layout (5 rows):
 * <pre>
 *   row 0: [filler × 9]
 *   row 1: [f] [ITEM:10] [f] [f] [QTY:13] [f] [f] [PRICE:16] [f]
 *   row 2: [filler × 9]
 *   row 3: [filler × 9]
 *   row 4: [f] [CANCEL:37] [f] [f] [f] [CONFIRM:41] [f] [f] [f]
 * </pre>
 * Clicking ITEM   → opens {@link OrderItemPickGUI}
 * Clicking QTY    → opens sign input
 * Clicking PRICE  → opens sign input
 * Clicking CONFIRM→ creates the order (requires all 3 fields filled)
 */
public class OrderCreateGUI extends GUIHolder {

    private static final int SLOT_ITEM    = 10;
    private static final int SLOT_QTY     = 13;
    private static final int SLOT_PRICE   = 16;
    private static final int SLOT_CANCEL  = 37;
    private static final int SLOT_CONFIRM = 41;

    private final SMPCore plugin;
    private boolean transitioning = false;

    public OrderCreateGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void open(Player p) {
        OrderManager.PendingCreation pending = plugin.orders().getOrCreatePending(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(this, 45,
                GUIUtil.title("<gradient:#f093fb:#f5576c><bold>Nouvelle commande</bold></gradient>"));
        this.inventory = inv;
        render(p, pending);
        p.openInventory(inv);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private void render(Player p, OrderManager.PendingCreation pending) {
        inventory.clear();
        for (int i = 0; i < 45; i++) inventory.setItem(i, GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE));

        // Item field
        if (pending.itemType != null) {
            inventory.setItem(SLOT_ITEM, GUIUtil.item(pending.itemType,
                    "<white>" + OrderManager.prettyMat(pending.itemType),
                    "<gray>Cliquer pour changer l'item",
                    "<dark_gray>← Champ: Item"));
        } else {
            inventory.setItem(SLOT_ITEM, GUIUtil.item(Material.BARRIER,
                    "<yellow>Choisir l'item",
                    "<gray>Cliquer pour ouvrir le sélecteur"));
        }

        // Quantity field
        if (pending.quantity > 0) {
            inventory.setItem(SLOT_QTY, GUIUtil.item(Material.PAPER,
                    "<white>Quantité: <aqua>" + pending.quantity,
                    "<gray>Cliquer pour modifier",
                    "<dark_gray>← Champ: Quantité"));
        } else {
            inventory.setItem(SLOT_QTY, GUIUtil.item(Material.PAPER,
                    "<yellow>Quantité",
                    "<gray>Cliquer → écrire sur le panneau"));
        }

        // Price/unit field
        if (pending.pricePerUnit > 0) {
            inventory.setItem(SLOT_PRICE, GUIUtil.item(Material.GOLD_INGOT,
                    "<white>Prix/u: <gold>" + Msg.money(pending.pricePerUnit) + "$",
                    "<gray>Cliquer pour modifier",
                    "<dark_gray>← Champ: Prix par unité"));
        } else {
            inventory.setItem(SLOT_PRICE, GUIUtil.item(Material.GOLD_INGOT,
                    "<yellow>Prix par unité",
                    "<gray>Cliquer → écrire sur le panneau"));
        }

        // Cancel
        inventory.setItem(SLOT_CANCEL, GUIUtil.item(Material.RED_STAINED_GLASS_PANE,
                "<red>Annuler",
                "<gray>Ferme sans créer de commande"));

        // Confirm (active only when all fields are set)
        if (pending.isReady()) {
            double total = pending.quantity * pending.pricePerUnit;
            inventory.setItem(SLOT_CONFIRM, GUIUtil.item(Material.LIME_STAINED_GLASS_PANE,
                    "<green><bold>Confirmer",
                    "<gray>Item: <white>" + OrderManager.prettyMat(pending.itemType),
                    "<gray>Quantité: <white>" + pending.quantity,
                    "<gray>Prix/u: <gold>" + Msg.money(pending.pricePerUnit) + "$",
                    "",
                    "<gray>Total escrow: <gold>" + Msg.money(total) + "$",
                    "<dark_gray>(prélevé de ton solde)"));
        } else {
            inventory.setItem(SLOT_CONFIRM, GUIUtil.item(Material.GRAY_STAINED_GLASS_PANE,
                    "<gray>Confirmer",
                    "<dark_gray>Remplis les 3 champs d'abord"));
        }
    }

    // ── Click ─────────────────────────────────────────────────────────────────

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        OrderManager.PendingCreation pending = plugin.orders().getOrCreatePending(player.getUniqueId());

        if (slot == SLOT_CANCEL) {
            transitioning = false;
            player.closeInventory();
            // closeInventory triggers onClose → removes pending
            return;
        }

        if (slot == SLOT_ITEM) {
            transitioning = true;
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                    new OrderItemPickGUI(plugin,
                            chosen -> {
                                pending.itemType = chosen;
                                new OrderCreateGUI(plugin).open(player);
                            },
                            () -> new OrderCreateGUI(plugin).open(player))
                            .open(player));
            return;
        }

        if (slot == SLOT_QTY) {
            transitioning = true;
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.signInput().open(player, "Quantite (entier)", text -> {
                        try {
                            int qty = Integer.parseInt(text.replace("_", "").replace(",", "").trim());
                            if (qty <= 0) { player.sendMessage(Msg.err("La quantité doit être > 0.")); }
                            else          { pending.quantity = qty; }
                        } catch (NumberFormatException e) {
                            player.sendMessage(Msg.err("Valeur non valide: \"" + text + "\""));
                        }
                        new OrderCreateGUI(plugin).open(player);
                    }, () -> new OrderCreateGUI(plugin).open(player)));
            return;
        }

        if (slot == SLOT_PRICE) {
            transitioning = true;
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.signInput().open(player, "Prix/u (ex: 100)", text -> {
                        double price = Msg.parseAmount(text);
                        if (price <= 0) { player.sendMessage(Msg.err("Prix invalide: \"" + text + "\"")); }
                        else            { pending.pricePerUnit = price; }
                        new OrderCreateGUI(plugin).open(player);
                    }, () -> new OrderCreateGUI(plugin).open(player)));
            return;
        }

        if (slot == SLOT_CONFIRM && pending.isReady()) {
            int id = plugin.orders().createOrder(
                    player, pending.itemType, pending.quantity, pending.pricePerUnit);
            if (id > 0) {
                plugin.orders().removePending(player.getUniqueId());
                transitioning = false;
                player.closeInventory();
                player.sendMessage(Msg.ok("Commande <gray>#" + id + "</gray> créée ! "
                        + "<gray>Les joueurs peuvent désormais te livrer des "
                        + OrderManager.prettyMat(pending.itemType) + "."));
            }
            // On failure, economy/manager already sent error message; keep GUI open
        }
    }

    @Override
    public void onClose(HumanEntity who) {
        if (!transitioning) {
            // Player actually quit the creation flow; discard draft
            plugin.orders().removePending(who.getUniqueId());
        }
        transitioning = false;
    }
}
