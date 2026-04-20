package fr.smp.core.gui;

import fr.smp.core.SMPCore;
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

    private final SMPCore plugin;
    private ShopManager.Category current;
    private int page = 0;
    private final Map<Integer, String> slotCategory = new HashMap<>();
    private final Map<Integer, ShopManager.ShopItem> slotItem = new HashMap<>();

    public ShopGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        current = null;
        page = 0;
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#fceabb:#f8b500><bold>Shop</bold></gradient>"));
        fillBackground(inv, Material.BLACK_STAINED_GLASS_PANE);

        slotCategory.clear();
        slotItem.clear();

        // Fixed, centered, evenly-spaced row for categories (6 large slots).
        int[] catSlots = {11, 13, 15, 29, 31, 33};
        int i = 0;
        for (ShopManager.Category cat : plugin.shop().categories().values()) {
            if (i >= catSlots.length) break;
            int slot = catSlots[i++];
            int count = cat.items().size();
            inv.setItem(slot, GUIUtil.item(cat.icon(),
                    "<yellow><bold>" + cat.displayName() + "</bold></yellow>",
                    "<gray>" + count + " items disponibles</gray>",
                    "",
                    "<green>▶ Clic pour ouvrir</green>"));
            slotCategory.put(slot, cat.id());
        }

        inv.setItem(49, GUIUtil.item(Material.BARRIER, "<red>Fermer</red>"));

        this.inventory = inv;
        p.openInventory(inv);
    }

    public void openCategory(Player p, ShopManager.Category cat, int page) {
        this.current = cat;
        this.page = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#fceabb:#f8b500><bold>" + cat.displayName() + "</bold></gradient>"));
        fillBackground(inv, Material.BLACK_STAINED_GLASS_PANE);

        slotCategory.clear();
        slotItem.clear();

        int[] inner = innerSlots();
        int perPage = inner.length;
        int offset = this.page * perPage;
        List<ShopManager.ShopItem> items = cat.items();

        for (int i = 0; i < perPage && offset + i < items.size(); i++) {
            ShopManager.ShopItem item = items.get(offset + i);
            ItemStack stack = new ItemStack(item.material(), Math.max(1, item.stack()));
            var meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(MM.deserialize("<!italic><yellow><bold>" +
                        prettyName(item.material()) + "</bold></yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(MM.deserialize("<!italic><dark_gray>──────────────</dark_gray>"));
                if (item.buyPrice() >= 0) {
                    lore.add(MM.deserialize("<!italic><green>Achat </green><gray>×" + item.stack() +
                            "</gray>  <yellow>$" + Msg.money(item.buyPrice()) + "</yellow>"));
                } else {
                    lore.add(MM.deserialize("<!italic><dark_gray>Achat indisponible</dark_gray>"));
                }
                if (item.sellPrice() >= 0) {
                    lore.add(MM.deserialize("<!italic><red>Vente </red><gray>×" + item.stack() +
                            "</gray>  <yellow>$" + Msg.money(item.sellPrice()) + "</yellow>"));
                } else {
                    lore.add(MM.deserialize("<!italic><dark_gray>Vente indisponible</dark_gray>"));
                }
                double w = plugin.worth().worth(item.material());
                if (w > 0) {
                    lore.add(MM.deserialize("<!italic><dark_gray>Worth: <gray>$" +
                            Msg.money(w) + "</gray> / unité</dark_gray>"));
                }
                lore.add(MM.deserialize("<!italic><dark_gray>──────────────</dark_gray>"));
                if (item.buyPrice() >= 0) lore.add(MM.deserialize("<!italic><green>▶ Clic gauche: Acheter</green>"));
                if (item.sellPrice() >= 0) lore.add(MM.deserialize("<!italic><red>▶ Clic droit: Vendre</red>"));
                if (item.sellPrice() >= 0) lore.add(MM.deserialize("<!italic><red>▶ Shift+droit: Vendre tout</red>"));
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inv.setItem(inner[i], stack);
            slotItem.put(inner[i], item);
        }

        if (page > 0) inv.setItem(45, GUIUtil.item(Material.ARROW, "<yellow>◀ Précédent</yellow>"));
        if ((page + 1) * perPage < items.size())
            inv.setItem(53, GUIUtil.item(Material.ARROW, "<yellow>Suivant ▶</yellow>"));
        inv.setItem(49, GUIUtil.item(Material.ARROW, "<yellow>◀ Retour</yellow>"));

        this.inventory = inv;
        p.openInventory(inv);
    }

    private int[] innerSlots() {
        int[] inner = new int[28];
        int idx = 0;
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c < 8; c++) inner[idx++] = r * 9 + c;
        }
        return inner;
    }

    private void fillBackground(Inventory inv, Material m) {
        ItemStack filler = GUIUtil.filler(m);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

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

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();
        if (current == null) {
            if (slot == 49) { p.closeInventory(); return; }
            String cat = slotCategory.get(slot);
            if (cat == null) return;
            ShopManager.Category c = plugin.shop().category(cat);
            if (c != null) openCategory(p, c, 0);
            return;
        }
        if (slot == 49) { open(p); return; }
        if (slot == 45) { openCategory(p, current, page - 1); return; }
        if (slot == 53) { openCategory(p, current, page + 1); return; }

        ShopManager.ShopItem item = slotItem.get(slot);
        if (item == null) return;

        ClickType click = event.getClick();
        if (click.isLeftClick()) buy(p, item);
        else if (click.isShiftClick() && click.isRightClick()) sellAll(p, item);
        else if (click.isRightClick()) sell(p, item);
    }

    private void buy(Player p, ShopManager.ShopItem item) {
        if (item.buyPrice() < 0) { p.sendMessage(Msg.err("Achat indisponible.")); return; }
        double cost = item.buyPrice();
        if (!plugin.economy().has(p.getUniqueId(), cost)) { p.sendMessage(Msg.err("Fonds insuffisants.")); return; }
        plugin.economy().withdraw(p.getUniqueId(), cost, "shop.buy " + item.material());
        var overflow = p.getInventory().addItem(new ItemStack(item.material(), item.stack()));
        overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        p.sendMessage(Msg.ok("<green>Acheté ×" + item.stack() + " " + prettyName(item.material()) +
                " pour $" + Msg.money(cost) + ".</green>"));
        plugin.logs().log(fr.smp.core.logging.LogCategory.SHOP, p, "buy " + item.material() + " x" + item.stack() + " $" + cost);
    }

    private void sell(Player p, ShopManager.ShopItem item) {
        if (item.sellPrice() < 0) { p.sendMessage(Msg.err("Vente indisponible.")); return; }
        int need = item.stack();
        int taken = takeItems(p, item.material(), need);
        if (taken < need) {
            // restore partial (not needed — we didn't remove unless >= need)
            p.sendMessage(Msg.err("Il te faut ×" + need + " " + prettyName(item.material()) + ".")); return;
        }
        plugin.economy().deposit(p.getUniqueId(), item.sellPrice(), "shop.sell " + item.material());
        p.sendMessage(Msg.ok("<green>Vendu ×" + need + " " + prettyName(item.material()) +
                " pour $" + Msg.money(item.sellPrice()) + ".</green>"));
        plugin.logs().log(fr.smp.core.logging.LogCategory.SHOP, p, "sell " + item.material() + " x" + need + " $" + item.sellPrice());
    }

    private void sellAll(Player p, ShopManager.ShopItem item) {
        if (item.sellPrice() < 0) { p.sendMessage(Msg.err("Vente indisponible.")); return; }
        int have = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && s.getType() == item.material()) have += s.getAmount();
        }
        int stacks = have / item.stack();
        if (stacks <= 0) { p.sendMessage(Msg.err("Rien à vendre.")); return; }
        int totalUnits = stacks * item.stack();
        takeItems(p, item.material(), totalUnits);
        double earn = stacks * item.sellPrice();
        plugin.economy().deposit(p.getUniqueId(), earn, "shop.sellall " + item.material());
        p.sendMessage(Msg.ok("<green>Vendu ×" + totalUnits + " " + prettyName(item.material()) +
                " pour $" + Msg.money(earn) + ".</green>"));
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
}
