package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.WealthManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class WealthGUI extends GUIHolder {

    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final SMPCore plugin;
    private final Map<Integer, WealthManager.WealthItem> slotItems = new HashMap<>();

    public WealthGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        slotItems.clear();
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f6d365:#fda085><bold>Argent utile</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        var data = plugin.players().get(player);
        String teamId = data != null ? data.teamId() : null;
        var team = teamId == null ? null : plugin.teams().get(teamId);

        inv.setItem(4, GUIUtil.item(Material.GOLD_BLOCK,
                "<gold><bold>Centre économique</bold></gold>",
                "<gray>Solde perso: <green>$" + Msg.money(plugin.economy().balance(player.getUniqueId())) + "</green></gray>",
                team == null
                        ? "<gray>Team: <white>Aucune</white></gray>"
                        : "<gray>Banque team: <green>$" + Msg.money(team.balance()) + "</green></gray>",
                "<gray>Dépensé: <yellow>$" + Msg.money(plugin.wealth().personalSpent(player.getUniqueId())) + "</yellow></gray>"));

        Map<WealthManager.Category, Integer> counters = new EnumMap<>(WealthManager.Category.class);
        int index = 0;
        for (WealthManager.WealthItem item : WealthManager.WealthItem.values()) {
            if (index >= ITEM_SLOTS.length) break;
            int slot = ITEM_SLOTS[index++];
            int count = counters.merge(item.category(), 1, Integer::sum);
            inv.setItem(slot, renderItem(player, teamId, item, count));
            slotItems.put(slot, item);
        }

        inv.setItem(45, GUIUtil.item(Material.ARROW,
                "<yellow><bold>Leaderboards</bold></yellow>",
                "<gray>Voir les classements classiques.</gray>",
                "",
                "<yellow>▶ Clic pour ouvrir</yellow>"));
        inv.setItem(49, GUIUtil.item(Material.BOOK,
                "<aqua><bold>Banque de team</bold></aqua>",
                "<gray>Déposer: <white>/team bank deposit <montant></white></gray>",
                "<gray>Retirer: <white>/team bank withdraw <montant></white></gray>",
                "<gray>Les achats team utilisent cette banque.</gray>"));
        inv.setItem(53, GUIUtil.item(Material.BARRIER, "<red><bold>Fermer</bold></red>"));

        this.inventory = inv;
        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack renderItem(Player player, String teamId, WealthManager.WealthItem item, int count) {
        String ownerId = item.ownerType() == WealthManager.OwnerType.PERSONAL
                ? player.getUniqueId().toString()
                : teamId;
        boolean unlocked = ownerId != null && plugin.wealth().isUnlocked(item.ownerType(), ownerId, item);
        boolean locked = !item.repeatable() && unlocked;
        Material icon = locked ? Material.LIME_DYE : item.icon();
        String status = locked
                ? "<green>Déjà débloqué</green>"
                : item.ownerType() == WealthManager.OwnerType.TEAM
                        ? "<aqua>Banque de team</aqua>"
                        : "<yellow>Achat perso</yellow>";

        return GUIUtil.item(icon,
                "<white><bold>" + item.displayName() + "</bold></white>",
                "<dark_gray>" + item.category().display() + " #" + count + "</dark_gray>",
                "",
                "<gray>" + item.description() + "</gray>",
                "<gray>Prix: <green>$" + Msg.money(item.price()) + "</green></gray>",
                "<gray>Type: " + status + "</gray>",
                "",
                locked ? "<dark_gray>Déjà acheté.</dark_gray>" : "<yellow>▶ Clic pour acheter</yellow>");
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;
        if (raw == 53) {
            player.closeInventory();
            return;
        }
        if (raw == 45) {
            new LeaderboardHubGUI(plugin).open(player);
            return;
        }
        WealthManager.WealthItem item = slotItems.get(raw);
        if (item == null) return;
        plugin.wealth().purchase(player, item);
        open(player);
    }
}
