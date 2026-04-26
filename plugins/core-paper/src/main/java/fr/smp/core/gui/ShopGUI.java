package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.ShopManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MAX_QTY = 64;

    private enum Mode { MAIN, CATEGORY, QUANTITY }
    private enum Action { BUY, SELL }

    private final SMPCore plugin;

    private Mode mode = Mode.MAIN;
    private ShopManager.Category currentCat;
    private int page = 0;
    private ShopManager.ShopItem currentItem;
    private Action currentAction;
    private int quantity = 1;

    private final Map<Integer, String> slotCategory = new HashMap<>();
    private final Map<Integer, ShopManager.ShopItem> slotBuyItem = new HashMap<>();
    private final Map<Integer, ShopManager.ShopItem> slotSellItem = new HashMap<>();

    private int slotBack = -1;
    private int slotClose = -1;
    private int slotPrev = -1;
    private int slotNext = -1;

    public ShopGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        openMain(p);
    }

    // ===== Main menu =====
    private void openMain(Player p) {
        mode = Mode.MAIN;
        currentCat = null;
        currentItem = null;
        clearMaps();

        List<ShopManager.Category> cats = new ArrayList<>(plugin.shop().categories().values());
        int count = cats.size();

        Inventory inv = Bukkit.createInventory(this, 27,
                GUIUtil.title("<gradient:#f59e0b:#ef4444><bold>Boutique</bold></gradient>"));
        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);

        int[] slots = centeredRow(count, 1);
        for (int i = 0; i < count; i++) {
            ShopManager.Category cat = cats.get(i);
            int n = cat.items().size();
            String plural = n > 1 ? "s" : "";
            List<String> lore = new ArrayList<>();
            if (cat.description() != null && !cat.description().isBlank()) {
                lore.add(cat.description());
                lore.add("");
            }
            lore.add("<gray>" + n + " article" + plural + " disponible" + plural + "</gray>");
            lore.add("");
            lore.add("<yellow>▶ Clic pour ouvrir</yellow>");
            inv.setItem(slots[i], GUIUtil.item(cat.icon(), cat.displayName(), lore.toArray(String[]::new)));
            slotCategory.put(slots[i], cat.id());
        }

        slotClose = 22;
        inv.setItem(slotClose, GUIUtil.item(Material.BARRIER, "<red><bold>Fermer</bold></red>"));

        this.inventory = inv;
        p.openInventory(inv);
    }

    // ===== Category menu =====
    private void openCategory(Player p, ShopManager.Category cat, int pageIdx) {
        mode = Mode.CATEGORY;
        currentCat = cat;
        this.page = Math.max(0, pageIdx);
        currentItem = null;
        clearMaps();

        List<ShopManager.ShopItem> items = cat.items();
        int total = items.size();
        int perPage = 21;
        int offset = this.page * perPage;
        int shown = Math.min(perPage, Math.max(0, total - offset));

        int rows = shown <= 7 ? 1 : (shown <= 14 ? 2 : 3);
        if (rows < 1) rows = 1;
        int size = (rows + 2) * 9;

        Inventory inv = Bukkit.createInventory(this, size, GUIUtil.title(cat.displayName()));
        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);

        int perRow = rows == 0 ? 0 : (int) Math.ceil((double) shown / rows);
        int placed = 0;
        for (int r = 0; r < rows && placed < shown; r++) {
            int rowCount = Math.min(perRow, shown - placed);
            int[] rowSlots = centeredRow(rowCount, r + 1);
            for (int i = 0; i < rowCount; i++) {
                ShopManager.ShopItem item = items.get(offset + placed + i);
                inv.setItem(rowSlots[i], buildCategoryItemStack(item));
                if (item.buyPrice() >= 0) slotBuyItem.put(rowSlots[i], item);
                if (item.sellPrice() >= 0) slotSellItem.put(rowSlots[i], item);
            }
            placed += rowCount;
        }

        int bottom = (rows + 1) * 9;
        boolean hasPrev = this.page > 0;
        boolean hasNext = (this.page + 1) * perPage < total;

        slotBack = bottom + 3;
        slotClose = bottom + 5;
        slotPrev = hasPrev ? bottom + 1 : -1;
        slotNext = hasNext ? bottom + 7 : -1;

        inv.setItem(slotBack, GUIUtil.item(Material.ARROW, "<yellow><bold>Retour</bold></yellow>",
                "<gray>Revenir au menu principal.</gray>"));
        inv.setItem(slotClose, GUIUtil.item(Material.BARRIER, "<red><bold>Fermer</bold></red>"));
        if (hasPrev) inv.setItem(slotPrev, GUIUtil.item(Material.SPECTRAL_ARROW, "<yellow>◀ Précédent</yellow>"));
        if (hasNext) inv.setItem(slotNext, GUIUtil.item(Material.SPECTRAL_ARROW, "<yellow>Suivant ▶</yellow>"));

        this.inventory = inv;
        p.openInventory(inv);
    }

    // ===== Quantity selector =====
    private void openQuantity(Player p, ShopManager.ShopItem item, Action action, int qty) {
        mode = Mode.QUANTITY;
        currentItem = item;
        currentAction = action;
        quantity = clampQty(p, item, action, qty);
        clearMaps();

        String title = action == Action.BUY
                ? "<gradient:#22c55e:#10b981><bold>Achat</bold></gradient>"
                : "<gradient:#f97316:#ef4444><bold>Vente</bold></gradient>";
        Inventory inv = Bukkit.createInventory(this, 27, GUIUtil.title(title));
        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);

        int max = maxQty(p, item, action);

        inv.setItem(10, GUIUtil.item(Material.RED_STAINED_GLASS_PANE,
                "<red><bold>−10</bold></red>",
                "<gray>Retirer 10 à la quantité.</gray>"));
        inv.setItem(11, GUIUtil.item(Material.RED_CONCRETE,
                "<red><bold>−1</bold></red>",
                "<gray>Retirer 1 à la quantité.</gray>"));

        inv.setItem(13, buildQuantityDisplay(item, action, max));

        inv.setItem(15, GUIUtil.item(Material.LIME_CONCRETE,
                "<green><bold>+1</bold></green>",
                "<gray>Ajouter 1 à la quantité.</gray>"));
        inv.setItem(16, GUIUtil.item(Material.LIME_STAINED_GLASS_PANE,
                "<green><bold>+10</bold></green>",
                "<gray>Ajouter 10 à la quantité.</gray>"));

        inv.setItem(20, GUIUtil.item(Material.ARROW, "<yellow><bold>Retour</bold></yellow>",
                "<gray>Revenir à la catégorie.</gray>"));
        inv.setItem(22, buildConfirmButton(item, action));
        inv.setItem(24, GUIUtil.item(Material.HOPPER, "<gold><bold>Max</bold></gold>",
                "<gray>Définir la quantité au maximum possible.</gray>",
                "",
                "<gray>Max: <white>×" + max + "</white></gray>"));

        this.inventory = inv;
        p.openInventory(inv);
    }

    // ===== Click routing =====
    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= (this.inventory == null ? 0 : this.inventory.getSize())) return;

        switch (mode) {
            case MAIN -> onClickMain(p, slot);
            case CATEGORY -> onClickCategory(p, slot, event.getClick());
            case QUANTITY -> onClickQuantity(p, slot);
        }
    }

    private void onClickMain(Player p, int slot) {
        if (slot == slotClose) { p.closeInventory(); return; }
        String catId = slotCategory.get(slot);
        if (catId == null) return;
        ShopManager.Category c = plugin.shop().category(catId);
        if (c != null) openCategory(p, c, 0);
    }

    private void onClickCategory(Player p, int slot, ClickType click) {
        if (slot == slotBack) { openMain(p); return; }
        if (slot == slotClose) { p.closeInventory(); return; }
        if (slot == slotPrev) { openCategory(p, currentCat, page - 1); return; }
        if (slot == slotNext) { openCategory(p, currentCat, page + 1); return; }

        ShopManager.ShopItem buy = slotBuyItem.get(slot);
        ShopManager.ShopItem sell = slotSellItem.get(slot);
        if (buy == null && sell == null) return;

        boolean rightClick = click != null && click.isRightClick();
        if (rightClick && sell != null) {
            openQuantity(p, sell, Action.SELL, 1);
        } else if (!rightClick && buy != null) {
            openQuantity(p, buy, Action.BUY, 1);
        } else if (sell != null) {
            openQuantity(p, sell, Action.SELL, 1);
        } else if (buy != null) {
            openQuantity(p, buy, Action.BUY, 1);
        }
    }

    private void onClickQuantity(Player p, int slot) {
        if (currentItem == null || currentAction == null) return;
        switch (slot) {
            case 10 -> openQuantity(p, currentItem, currentAction, quantity - 10);
            case 11 -> openQuantity(p, currentItem, currentAction, quantity - 1);
            case 15 -> openQuantity(p, currentItem, currentAction, quantity + 1);
            case 16 -> openQuantity(p, currentItem, currentAction, quantity + 10);
            case 20 -> {
                ShopManager.Category back = currentCat != null
                        ? currentCat
                        : findCategoryOf(currentItem);
                if (back != null) openCategory(p, back, page);
                else openMain(p);
            }
            case 22 -> confirm(p);
            case 24 -> openQuantity(p, currentItem, currentAction, maxQty(p, currentItem, currentAction));
        }
    }

    // ===== Purchase / Sale =====
    private void confirm(Player p) {
        ShopManager.ShopItem item = currentItem;
        Action action = currentAction;
        if (item == null || action == null) return;
        int qty = clampQty(p, item, action, quantity);
        if (qty <= 0) { p.sendMessage(Msg.err("Quantité invalide.")); return; }

        if (action == Action.BUY) doBuy(p, item, qty);
        else doSell(p, item, qty);

        ShopManager.Category back = currentCat != null ? currentCat : findCategoryOf(item);
        if (back != null) openCategory(p, back, page);
        else openMain(p);
    }

    private void doBuy(Player p, ShopManager.ShopItem item, int qty) {
        if (item.buyPrice() < 0) { p.sendMessage(Msg.err("Achat indisponible.")); return; }

        int unit = Math.max(1, item.stack());
        int totalItems = qty * unit;

        if (item.buyCurrency() == ShopManager.Currency.SHARDS) {
            PlayerData data = plugin.players().get(p);
            if (data == null) { p.sendMessage(Msg.err("Profil joueur introuvable.")); return; }
            long cost = Math.round(item.buyPrice() * qty);
            if (data.shards() < cost) { p.sendMessage(Msg.err("Saphirs insuffisants.")); return; }
            data.addShards(-cost);
            giveItems(p, item, totalItems);
            p.sendMessage(Msg.ok("<green>Acheté ×" + totalItems + " " + displayName(item)
                    + " pour <aqua>" + formatAmount(cost) + " saphirs</aqua>.</green>"));
            plugin.logs().log(fr.smp.core.logging.LogCategory.SHOP, p,
                    "buy " + item.id() + " x" + qty + " " + formatAmount(cost) + " shards");
            return;
        }

        double cost = item.buyPrice() * qty;
        if (!plugin.economy().has(p.getUniqueId(), cost)) { p.sendMessage(Msg.err("Fonds insuffisants.")); return; }
        plugin.economy().withdraw(p.getUniqueId(), cost, "shop.buy " + item.material());
        giveItems(p, item, totalItems);
        p.sendMessage(Msg.ok("<green>Acheté ×" + totalItems + " " + displayName(item)
                + " pour $" + Msg.money(cost) + ".</green>"));
        plugin.logs().log(fr.smp.core.logging.LogCategory.SHOP, p,
                "buy " + item.id() + " x" + qty + " $" + cost);
    }

    private void doSell(Player p, ShopManager.ShopItem item, int qty) {
        if (item.sellPrice() < 0) { p.sendMessage(Msg.err("Vente indisponible.")); return; }
        int unit = Math.max(1, item.stack());
        int need = qty * unit;
        int taken = takeItems(p, item.material(), need);
        if (taken < need) {
            p.sendMessage(Msg.err("Il te faut ×" + need + " " + displayName(item) + "."));
            return;
        }
        double earn = item.sellPrice() * qty;
        plugin.economy().deposit(p.getUniqueId(), earn, "shop.sell " + item.material());
        p.sendMessage(Msg.ok("<green>Vendu ×" + need + " " + displayName(item)
                + " pour $" + Msg.money(earn) + ".</green>"));
        plugin.logs().log(fr.smp.core.logging.LogCategory.SHOP, p,
                "sell " + item.material() + " x" + need + " $" + earn);
    }

    private void giveItems(Player p, ShopManager.ShopItem item, int totalItems) {
        int remaining = totalItems;
        int maxStack = item.material().getMaxStackSize();
        if (maxStack <= 0) maxStack = 64;
        while (remaining > 0) {
            int amt = Math.min(remaining, maxStack);
            ItemStack chunk;
            if (item.spawnerType() != null && plugin.spawners() != null) {
                chunk = plugin.spawners().makeSpawnerItem(item.spawnerType(), amt);
            } else {
                chunk = new ItemStack(item.material(), amt);
            }
            if (chunk == null) { p.sendMessage(Msg.err("Cet article n'est pas disponible.")); return; }
            var overflow = p.getInventory().addItem(chunk);
            overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            remaining -= amt;
        }
    }

    private int takeItems(Player p, Material m, int count) {
        int have = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && s.getType() == m) have += s.getAmount();
        }
        if (have < count) return have;
        int left = count;
        ItemStack[] cts = p.getInventory().getContents();
        for (int i = 0; i < cts.length && left > 0; i++) {
            ItemStack s = cts[i];
            if (s == null || s.getType() != m) continue;
            int take = Math.min(left, s.getAmount());
            s.setAmount(s.getAmount() - take);
            left -= take;
            if (s.getAmount() <= 0) cts[i] = null;
        }
        p.getInventory().setContents(cts);
        return count;
    }

    // ===== Quantity helpers =====
    private int maxQty(Player p, ShopManager.ShopItem item, Action action) {
        if (action == Action.BUY) {
            if (item.buyPrice() <= 0) return MAX_QTY;
            if (item.buyCurrency() == ShopManager.Currency.SHARDS) {
                PlayerData data = plugin.players().get(p);
                long shards = data != null ? data.shards() : 0;
                int canAfford = (int) Math.max(0, Math.floor(shards / item.buyPrice()));
                return Math.max(1, Math.min(MAX_QTY, canAfford));
            }
            double bal = plugin.economy().balance(p.getUniqueId());
            int canAfford = (int) Math.max(0, Math.floor(bal / item.buyPrice()));
            return Math.max(1, Math.min(MAX_QTY, canAfford));
        } else {
            int have = 0;
            for (ItemStack s : p.getInventory().getContents()) {
                if (s != null && s.getType() == item.material()) have += s.getAmount();
            }
            return Math.max(1, Math.min(MAX_QTY, have / Math.max(1, item.stack())));
        }
    }

    private int clampQty(Player p, ShopManager.ShopItem item, Action action, int qty) {
        int max = maxQty(p, item, action);
        if (qty < 1) return 1;
        if (qty > max) return max;
        return qty;
    }

    // ===== Item builders =====
    private ItemStack buildCategoryItemStack(ShopManager.ShopItem item) {
        ItemStack stack = new ItemStack(
                item.displayMaterial(),
                Math.max(1, Math.min(item.stack(), item.displayMaterial().getMaxStackSize()))
        );
        var meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(MM.deserialize("<!italic><yellow><bold>" + displayName(item) + "</bold></yellow>"));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic><dark_gray>──────────────</dark_gray>"));
        if (item.buyPrice() >= 0) {
            if (item.buyCurrency() == ShopManager.Currency.SHARDS) {
                lore.add(MM.deserialize("<!italic><green>Achat </green><gray>×" + item.stack()
                        + "</gray>  <aqua>◆ " + formatAmount(item.buyPrice()) + " saphirs</aqua>"));
            } else {
                lore.add(MM.deserialize("<!italic><green>Achat </green><gray>×" + item.stack()
                        + "</gray>  <yellow>$" + Msg.money(item.buyPrice()) + "</yellow>"));
            }
        }
        if (item.sellPrice() >= 0) {
            lore.add(MM.deserialize("<!italic><red>Vente </red><gray>×" + item.stack()
                    + "</gray>  <yellow>$" + Msg.money(item.sellPrice()) + "</yellow>"));
        }
        if (item.spawnerType() != null) {
            lore.add(MM.deserialize("<!italic><dark_gray>Donne un spawner custom </dark_gray>"
                    + item.spawnerType().colorTag() + item.spawnerType().display()));
        }
        lore.add(MM.deserialize("<!italic><dark_gray>──────────────</dark_gray>"));
        if (item.buyPrice() >= 0) {
            lore.add(MM.deserialize("<!italic><green>▶ Clic gauche: Acheter</green>"));
        }
        if (item.sellPrice() >= 0) {
            lore.add(MM.deserialize("<!italic><red>▶ Clic droit: Vendre</red>"));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildQuantityDisplay(ShopManager.ShopItem item, Action action, int max) {
        int visual = Math.max(1, Math.min(quantity, Math.max(1, item.displayMaterial().getMaxStackSize())));
        ItemStack stack = new ItemStack(item.displayMaterial(), visual);
        var meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(MM.deserialize("<!italic><yellow><bold>" + displayName(item) + "</bold></yellow>"));

        int unit = Math.max(1, item.stack());
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic><dark_gray>──────────────</dark_gray>"));
        lore.add(MM.deserialize("<!italic><gray>Quantité: <white>×" + quantity + "</white>"
                + (quantity >= max ? " <dark_gray>(max)</dark_gray>" : "") + "</gray>"));
        if (unit > 1) {
            lore.add(MM.deserialize("<!italic><gray>Items: <white>" + (quantity * unit) + "</white></gray>"));
        }
        lore.add(MM.deserialize("<!italic><dark_gray>──────────────</dark_gray>"));
        if (action == Action.BUY) {
            double unitPrice = item.buyPrice();
            double total = unitPrice * quantity;
            if (item.buyCurrency() == ShopManager.Currency.SHARDS) {
                lore.add(MM.deserialize("<!italic><gray>Unitaire: <aqua>◆ " + formatAmount(unitPrice) + "</aqua></gray>"));
                lore.add(MM.deserialize("<!italic><green>Total: <aqua>◆ " + formatAmount(total) + " saphirs</aqua></green>"));
            } else {
                lore.add(MM.deserialize("<!italic><gray>Unitaire: <yellow>$" + Msg.money(unitPrice) + "</yellow></gray>"));
                lore.add(MM.deserialize("<!italic><green>Total: <yellow>$" + Msg.money(total) + "</yellow></green>"));
            }
        } else {
            double unitPrice = item.sellPrice();
            double total = unitPrice * quantity;
            lore.add(MM.deserialize("<!italic><gray>Unitaire: <yellow>$" + Msg.money(unitPrice) + "</yellow></gray>"));
            lore.add(MM.deserialize("<!italic><green>Gain: <yellow>$" + Msg.money(total) + "</yellow></green>"));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildConfirmButton(ShopManager.ShopItem item, Action action) {
        String name = action == Action.BUY
                ? "<green><bold>Confirmer l'achat</bold></green>"
                : "<red><bold>Confirmer la vente</bold></red>";
        List<String> lore = new ArrayList<>();
        if (action == Action.BUY) {
            double total = item.buyPrice() * quantity;
            if (item.buyCurrency() == ShopManager.Currency.SHARDS) {
                lore.add("<gray>Prix total: <aqua>◆ " + formatAmount(total) + " saphirs</aqua></gray>");
            } else {
                lore.add("<gray>Prix total: <yellow>$" + Msg.money(total) + "</yellow></gray>");
            }
        } else {
            double total = item.sellPrice() * quantity;
            lore.add("<gray>Gain total: <yellow>$" + Msg.money(total) + "</yellow></gray>");
        }
        lore.add("");
        lore.add("<green>▶ Clic pour confirmer</green>");
        Material m = action == Action.BUY ? Material.EMERALD_BLOCK : Material.GOLD_BLOCK;
        return GUIUtil.item(m, name, lore.toArray(String[]::new));
    }

    // ===== Layout helpers =====
    private int[] centeredRow(int count, int row) {
        if (count <= 0) return new int[0];
        int[] slots = new int[count];
        int start = row * 9;
        int width1 = 2 * count - 1;
        if (width1 <= 9) {
            int leftPad = (9 - width1) / 2;
            for (int i = 0; i < count; i++) slots[i] = start + leftPad + i * 2;
        } else {
            int leftPad = (9 - count) / 2;
            for (int i = 0; i < count; i++) slots[i] = start + leftPad + i;
        }
        return slots;
    }

    private void fillAll(Inventory inv, Material m) {
        ItemStack filler = GUIUtil.filler(m);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void clearMaps() {
        slotCategory.clear();
        slotBuyItem.clear();
        slotSellItem.clear();
        slotBack = -1;
        slotClose = -1;
        slotPrev = -1;
        slotNext = -1;
    }

    private ShopManager.Category findCategoryOf(ShopManager.ShopItem item) {
        if (item == null) return null;
        for (ShopManager.Category c : plugin.shop().categories().values()) {
            for (ShopManager.ShopItem i : c.items()) {
                if (i == item || i.id().equals(item.id())) return c;
            }
        }
        return null;
    }

    // ===== Formatting =====
    private String prettyName(Material m) {
        String s = m.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            sb.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ';
        }
        return sb.toString();
    }

    private String displayName(ShopManager.ShopItem item) {
        if (item.displayName() != null && !item.displayName().isBlank()) return item.displayName();
        if (item.spawnerType() != null) return "Spawner " + item.spawnerType().display();
        return prettyName(item.material());
    }

    private String formatAmount(double value) {
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return Msg.money(value);
    }
}
