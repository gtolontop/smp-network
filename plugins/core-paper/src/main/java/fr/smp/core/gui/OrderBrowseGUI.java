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

import java.util.ArrayList;
import java.util.List;

/**
 * 54-slot browse GUI for buy orders.
 *
 * Layout:
 * <pre>
 *   rows 0-4 (slots 0-44)  — order cards, 45 per page
 *   row 5    (slots 45-53) — controls
 *     45: prev page
 *     46: sort cycle
 *     47: filter info
 *     48-51: filler
 *     52: create order
 *     53: next page
 * </pre>
 */
public class OrderBrowseGUI extends GUIHolder {

    private static final int PAGE_SIZE  = 45;
    private static final int SLOT_PREV   = 45;
    private static final int SLOT_SORT   = 46;
    private static final int SLOT_FILTER = 47;
    private static final int SLOT_CREATE = 52;
    private static final int SLOT_NEXT   = 53;

    // ── Filter ────────────────────────────────────────────────────────────────

    public sealed interface Filter permits Filter.All, Filter.ByPlayer, Filter.ByItem {
        record All()              implements Filter {}
        record ByPlayer(String name) implements Filter {}
        record ByItem(Material item) implements Filter {}

        static Filter all()              { return new All(); }
        static Filter player(String name){ return new ByPlayer(name); }
        static Filter item(Material m)   { return new ByItem(m); }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final SMPCore plugin;
    private final Filter filter;
    private OrderSort sort;
    private int page = 0;
    private List<Order> current = List.of();

    public OrderBrowseGUI(SMPCore plugin, Filter filter, OrderSort sort) {
        this.plugin = plugin;
        this.filter = filter;
        this.sort   = sort;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void open(Player p) {
        current = load();
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f093fb:#f5576c><bold>Commandes</bold></gradient>"));
        this.inventory = inv;
        render(p);
        p.openInventory(inv);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private void render(Player viewer) {
        inventory.clear();

        ItemStack filler = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        // Order cards
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < current.size(); i++) {
            inventory.setItem(i, buildCard(current.get(start + i), viewer));
        }

        // Pagination
        boolean hasPrev = page > 0;
        boolean hasNext = (page + 1) * PAGE_SIZE < current.size();
        int totalPages  = Math.max(1, (int) Math.ceil(current.size() / (double) PAGE_SIZE));

        inventory.setItem(SLOT_PREV, hasPrev
                ? GUIUtil.item(Material.ARROW, "<white>← Page précédente",
                        "<gray>Page " + page + "/" + totalPages)
                : filler);
        inventory.setItem(SLOT_NEXT, hasNext
                ? GUIUtil.item(Material.ARROW, "<white>Page suivante →",
                        "<gray>Page " + (page + 2) + "/" + totalPages)
                : filler);

        // Sort
        inventory.setItem(SLOT_SORT, GUIUtil.item(Material.HOPPER,
                "<yellow>Tri: <white>" + sort.getLabel(),
                "<gray>Cliquer pour changer",
                "",
                "<dark_gray>Prix ↓/↑ · Quantité ↓/↑ · Date"));

        // Filter label
        String filterLabel = switch (filter) {
            case Filter.All      ignored -> "<gray>Toutes les commandes";
            case Filter.ByPlayer f      -> "<gray>Commandes de <white>" + f.name();
            case Filter.ByItem   f      -> "<gray>Item: <white>" + OrderManager.prettyMat(f.item());
        };
        inventory.setItem(SLOT_FILTER, GUIUtil.item(Material.COMPASS,
                "<yellow>Filtre actif", filterLabel, "",
                "<dark_gray>" + current.size() + " commande" + (current.size() != 1 ? "s" : "")));

        // Create button
        inventory.setItem(SLOT_CREATE, GUIUtil.item(Material.WRITABLE_BOOK,
                "<green><bold>+ Créer une commande</bold>",
                "<gray>Achète des ressources à d'autres joueurs",
                "<gray>Tu places une offre d'achat avec escrow"));
    }

    private ItemStack buildCard(Order order, Player viewer) {
        boolean isOwn = order.getBuyerUuid().equals(viewer.getUniqueId().toString());
        int inInv = isOwn ? 0 : countInInv(viewer, order.getItemType());

        List<String> lore = new ArrayList<>();
        lore.add("<gray>De: <white>" + order.getBuyerName());
        lore.add("<gray>Item: <white>" + OrderManager.prettyMat(order.getItemType()));
        lore.add("<gray>Restant: <white>" + order.getRemainingQuantity() + "<gray>/" + order.getQuantity());
        lore.add("<gray>Prix: <gold>" + Msg.money(order.getPricePerUnit()) + "$<gray>/u");
        lore.add("<gray>Total: <gold>" + Msg.money(order.getRemainingValue()) + "$");
        lore.add("");
        if (isOwn) {
            lore.add("<yellow>C'est ta commande");
            lore.add("<red>Shift+clic → Annuler");
        } else {
            lore.add("<gray>Tu as: <white>" + inInv + " " + OrderManager.prettyMat(order.getItemType()));
            lore.add(inInv > 0 ? "<green>Clic → Livrer" : "<dark_gray>Clic → Voir les détails");
        }

        String namePrefix = isOwn ? "<yellow>[Toi] " : "<aqua>[" + order.getBuyerName() + "] ";
        return GUIUtil.item(order.getItemType(),
                namePrefix + OrderManager.prettyMat(order.getItemType()),
                lore.toArray(new String[0]));
    }

    // ── Click ─────────────────────────────────────────────────────────────────

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == SLOT_SORT) {
            sort = sort.next();
            current = load();
            page = 0;
            render(player);
            return;
        }
        if (slot == SLOT_CREATE) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> new OrderCreateGUI(plugin).open(player));
            return;
        }
        if (slot == SLOT_PREV && page > 0)                              { page--; render(player); return; }
        if (slot == SLOT_NEXT && (page + 1) * PAGE_SIZE < current.size()) { page++; render(player); return; }

        // Order card click
        if (slot >= 0 && slot < PAGE_SIZE) {
            int idx = page * PAGE_SIZE + slot;
            if (idx >= current.size()) return;
            Order order = current.get(idx);
            boolean isOwn = order.getBuyerUuid().equals(player.getUniqueId().toString());

            if (isOwn && event.isShiftClick()) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> new OrderActionGUI(plugin, order, true).open(player));
            } else if (!isOwn) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> new OrderActionGUI(plugin, order, false).open(player));
            }
        }
    }

    @Override
    public void onClose(HumanEntity who) {}

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<Order> load() {
        return switch (filter) {
            case Filter.All      ignored -> plugin.orders().getActiveOrders(sort);
            case Filter.ByPlayer f      -> plugin.orders().getByPlayer(f.name(), sort);
            case Filter.ByItem   f      -> plugin.orders().getByItem(f.item(), sort);
        };
    }

    private int countInInv(Player p, Material m) {
        int count = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m) count += it.getAmount();
        }
        return count;
    }
}
