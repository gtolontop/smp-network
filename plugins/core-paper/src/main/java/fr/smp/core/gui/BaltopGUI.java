package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerDataManager.MoneyEntry;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class BaltopGUI extends GUIHolder {

    private final SMPCore plugin;

    public BaltopGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f6d365:#fda085><bold>Baltop</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        List<MoneyEntry> top = plugin.players().topMoney(10);

        // Podium: 1st in slot 13, 2nd in 11, 3rd in 15
        int[] podiumSlots = {11, 13, 15};
        Material[] podiumMats = {Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK};
        String[] podiumLabels = {"2", "1", "3"};
        String[] podiumColors = {"<white>", "<gold>", "<aqua>"};

        if (top.size() > 1) setPodium(inv, 11, 2, top.get(1), Material.IRON_BLOCK, "<white>");
        if (!top.isEmpty()) setPodium(inv, 13, 1, top.get(0), Material.GOLD_BLOCK, "<gold>");
        if (top.size() > 2) setPodium(inv, 15, 3, top.get(2), Material.DIAMOND_BLOCK, "<aqua>");

        // Rows of the rest: slots 28..34 (rank 4..10)
        int[] listSlots = {28, 29, 30, 31, 32, 33, 34};
        for (int i = 3; i < top.size() && i - 3 < listSlots.length; i++) {
            MoneyEntry e = top.get(i);
            ItemStack head = headOf(e.uuid(), e.name());
            var meta = head.getItemMeta();
            meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<!italic><gray>#" + (i + 1) + " <white>" + e.name() + "</white></gray>"));
            meta.lore(java.util.List.of(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<!italic><green>$" + Msg.money(e.money()) + "</green>")
            ));
            head.setItemMeta(meta);
            inv.setItem(listSlots[i - 3], head);
        }

        if (top.isEmpty()) {
            inv.setItem(22, GUIUtil.item(Material.BARRIER, "<red>Aucune donnée</red>",
                    "<gray>Aucun joueur enregistré.</gray>"));
        }

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private void setPodium(Inventory inv, int slot, int rank, MoneyEntry e, Material fallback, String color) {
        ItemStack item = headOf(e.uuid(), e.name());
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<!italic>" + color + "<bold>#" + rank + " " + e.name() + "</bold><reset>"));
        meta.lore(java.util.List.of(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<!italic><green>$" + Msg.money(e.money()) + "</green>")
        ));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private ItemStack headOf(java.util.UUID uuid, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            head.setItemMeta(sm);
        }
        return head;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // read-only
    }
}
