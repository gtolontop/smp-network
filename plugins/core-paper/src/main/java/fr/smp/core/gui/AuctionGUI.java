package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.AuctionManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final Map<Integer, Long> slotListing = new HashMap<>();
    private int page = 0;
    private boolean mineView = false;

    public AuctionGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, int page) {
        this.mineView = false;
        this.page = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#ffecd2:#fcb69f><bold>Auction House</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        slotListing.clear();
        List<AuctionManager.Listing> all = plugin.auction().active();
        int perPage = 28;
        int offset = page * perPage;
        int[] inner = innerSlots();
        for (int i = 0; i < perPage; i++) {
            int idx = offset + i;
            if (idx >= all.size()) break;
            AuctionManager.Listing l = all.get(idx);
            ItemStack clone = l.item().clone();
            ItemMeta m = clone.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (m.lore() != null) lore.addAll(m.lore());
            lore.add(MM.deserialize("<!italic> "));
            lore.add(MM.deserialize("<!italic><gold>Prix: <yellow>$" + Msg.money(l.price()) + "</yellow></gold>"));
            lore.add(MM.deserialize("<!italic><gray>Vendeur: <white>" + l.sellerName() + "</white></gray>"));
            long left = (l.expiresAt() - System.currentTimeMillis()) / 1000;
            lore.add(MM.deserialize("<!italic><gray>Expire dans <white>" + Msg.duration(left) + "</white></gray>"));
            double worth = plugin.worth().worth(l.item());
            if (worth > 0) {
                lore.add(MM.deserialize("<!italic><dark_gray>Worth: <gray>$" + Msg.money(worth) + "</gray></dark_gray>"));
            }
            lore.add(MM.deserialize("<!italic> "));
            lore.add(MM.deserialize("<!italic><green>▶ Clic pour acheter</green>"));
            m.lore(lore);
            clone.setItemMeta(m);
            inv.setItem(inner[i], clone);
            slotListing.put(inner[i], l.id());
        }

        if (page > 0) inv.setItem(45, GUIUtil.item(Material.ARROW, "<yellow>◀ Page précédente</yellow>"));
        if ((page + 1) * perPage < all.size()) inv.setItem(53, GUIUtil.item(Material.ARROW, "<yellow>Page suivante ▶</yellow>"));
        inv.setItem(49, GUIUtil.item(Material.PAPER,
                "<gold><bold>Mes ventes</bold></gold>",
                "<gray>Clic pour ouvrir.</gray>"));

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

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();

        if (mineView) {
            if (slot == 49) { open(p, 0); return; }
            Long id = slotListing.get(slot);
            if (id == null) return;
            cancelOwn(p, id);
            return;
        }

        if (slot == 45) { open(p, page - 1); return; }
        if (slot == 53) { open(p, page + 1); return; }
        if (slot == 49) { openMine(p); return; }

        Long id = slotListing.get(slot);
        if (id == null) return;

        AuctionManager.Listing l = plugin.auction().get(id);
        if (l == null || l.expiresAt() < System.currentTimeMillis()) {
            p.sendMessage(Msg.err("Cette annonce n'est plus disponible."));
            open(p, page);
            return;
        }
        if (l.seller().equals(p.getUniqueId())) {
            p.sendMessage(Msg.err("C'est ta propre annonce."));
            return;
        }
        if (!plugin.economy().has(p.getUniqueId(), l.price())) {
            p.sendMessage(Msg.err("Fonds insuffisants."));
            return;
        }
        if (!plugin.auction().markSold(id)) {
            p.sendMessage(Msg.err("Achat raté (déjà vendu)."));
            open(p, page);
            return;
        }
        plugin.economy().withdraw(p.getUniqueId(), l.price(), "auction.buy #" + id);
        plugin.economy().deposit(l.seller(), l.price(), "auction.sold #" + id);
        var overflow = p.getInventory().addItem(l.item().clone());
        overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));

        p.sendMessage(Msg.ok("<green>Acheté pour $" + Msg.money(l.price()) + ".</green>"));
        Player seller = Bukkit.getPlayer(l.seller());
        String itemName = l.item().hasItemMeta() && l.item().getItemMeta().hasDisplayName()
                ? l.item().getItemMeta().getDisplayName() : l.item().getType().name();
        if (seller != null) {
            seller.sendMessage(Msg.info("<green>Ton item <white>" + itemName
                    + "</white> a été vendu pour <yellow>$" + Msg.money(l.price())
                    + "</yellow> à <white>" + p.getName() + "</white>.</green>"));
        } else {
            plugin.auction().saveSoldNotification(l.seller(), p.getName(), l.price(), itemName);
        }
        plugin.logs().log(LogCategory.AUCTION, p, "buy id=" + id + " price=" + l.price());
        open(p, page);
    }

    public void openMine(Player p) {
        this.mineView = true;
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gold><bold>Mes ventes</bold></gold>"));
        GUIUtil.fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);
        slotListing.clear();

        List<AuctionManager.Listing> mine = plugin.auction().ofSeller(p.getUniqueId());
        int[] inner = innerSlots();
        for (int i = 0; i < mine.size() && i < inner.length; i++) {
            AuctionManager.Listing l = mine.get(i);
            ItemStack clone = l.item().clone();
            ItemMeta m = clone.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (m.lore() != null) lore.addAll(m.lore());
            lore.add(MM.deserialize("<!italic> "));
            lore.add(MM.deserialize("<!italic><gold>Prix: <yellow>$" + Msg.money(l.price()) + "</yellow></gold>"));
            long left = (l.expiresAt() - System.currentTimeMillis()) / 1000;
            lore.add(MM.deserialize("<!italic><gray>Expire dans <white>" + Msg.duration(Math.max(0, left)) + "</white></gray>"));
            lore.add(MM.deserialize("<!italic><red>▶ Clic pour retirer</red>"));
            m.lore(lore);
            clone.setItemMeta(m);
            inv.setItem(inner[i], clone);
            slotListing.put(inner[i], l.id());
        }

        inv.setItem(49, GUIUtil.item(Material.ARROW, "<yellow>◀ Retour</yellow>"));
        this.inventory = inv;
        p.openInventory(inv);
    }

    private void cancelOwn(Player p, long id) {
        AuctionManager.Listing l = plugin.auction().get(id);
        if (l == null || !l.seller().equals(p.getUniqueId())) {
            p.sendMessage(Msg.err("Annonce introuvable."));
            openMine(p);
            return;
        }
        if (!plugin.auction().markSold(id)) {
            p.sendMessage(Msg.err("Déjà retiré."));
            openMine(p);
            return;
        }
        var overflow = p.getInventory().addItem(l.item().clone());
        overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        p.sendMessage(Msg.ok("<green>Annonce retirée.</green>"));
        plugin.logs().log(LogCategory.AUCTION, p, "cancel id=" + id);
        openMine(p);
    }
}
