package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.LeaderboardManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class LeaderboardHubGUI extends GUIHolder {

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final LeaderboardManager.Category[] CATEGORIES = {
            LeaderboardManager.Category.MONEY,
            LeaderboardManager.Category.PLAYTIME,
            LeaderboardManager.Category.KILLS,
            LeaderboardManager.Category.DEATHS,
            LeaderboardManager.Category.DISTANCE,
            LeaderboardManager.Category.DUEL_ELO
    };

    private final SMPCore plugin;

    public LeaderboardHubGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        Inventory inv = Bukkit.createInventory(this, 27,
                GUIUtil.title("<gradient:#f6d365:#fda085><bold>Leaderboards</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        for (int i = 0; i < CATEGORIES.length; i++) {
            LeaderboardManager.Category cat = CATEGORIES[i];
            inv.setItem(CATEGORY_SLOTS[i], GUIUtil.item(cat.icon(),
                    "<white><bold>" + cat.display() + "</bold></white>",
                    "<gray>" + descriptionFor(cat) + "</gray>",
                    "",
                    "<yellow>▶ Clic pour ouvrir</yellow>"));
        }

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private String descriptionFor(LeaderboardManager.Category cat) {
        return switch (cat) {
            case MONEY -> "Joueurs et teams les plus riches.";
            case PLAYTIME -> "Temps total cumulé sur le réseau.";
            case KILLS -> "Plus gros chasseurs du serveur.";
            case DEATHS -> "Les morts comptent aussi.";
            case DISTANCE -> "Stats vanilla de déplacement.";
            case DUEL_ELO -> "Classement ELO des duels PvP.";
        };
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;

        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            if (raw == CATEGORY_SLOTS[i]) {
                new LeaderboardGUI(plugin).open(player, CATEGORIES[i], LeaderboardManager.Scope.SOLO, 0);
                return;
            }
        }
    }
}
