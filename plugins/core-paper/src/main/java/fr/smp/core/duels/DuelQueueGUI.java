package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.GUIHolder;
import fr.smp.core.gui.GUIUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 9-slot top row of a 5-row inventory: cancel | sword (queue) | spectate |
 * stats | confirm. Layout mirrors the Donut SMP screenshots — same columns,
 * same colour cues — so players don't have to relearn the flow.
 *
 * State changes per click are owned by the manager; this GUI is read-only
 * once opened. We refresh by re-opening rather than mutating the slots.
 */
public final class DuelQueueGUI extends GUIHolder {

    private static final int SIZE = 27;

    private final SMPCore plugin;
    private final Player viewer;

    private DuelQueueGUI(SMPCore plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public static void open(SMPCore plugin, Player viewer) {
        DuelQueueGUI g = new DuelQueueGUI(plugin, viewer);
        Inventory inv = Bukkit.createInventory(g, SIZE,
                GUIUtil.title("<dark_gray>» <gold>Duels</gold> <gray>« Sélectionne une option"));
        g.inventory = inv;
        g.render();
        viewer.openInventory(inv);
    }

    private void render() {
        GUIUtil.fillBorder(inventory, Material.ORANGE_STAINED_GLASS_PANE);

        boolean queued = plugin.duelQueue() != null && plugin.duelQueue().isQueued(viewer.getUniqueId());
        DuelMatch myMatch = plugin.duelMatches() != null ? plugin.duelMatches().byPlayer(viewer.getUniqueId()) : null;

        // Slot 11 — sword: queue / cancel
        if (myMatch != null) {
            inventory.setItem(11, GUIUtil.item(Material.NETHERITE_SWORD,
                    "<gold>EN MATCH</gold>",
                    "<gray>Match en cours, retour automatique.</gray>"));
        } else if (queued) {
            inventory.setItem(11, GUIUtil.item(Material.RED_STAINED_GLASS_PANE,
                    "<red><bold>CANCEL</bold></red>",
                    "<gray>Click to cancel</gray>"));
        } else {
            inventory.setItem(11, GUIUtil.item(Material.IRON_SWORD,
                    "<green><bold>SEARCH</bold></green>",
                    "<gray>Click to start searching for a match</gray>"));
        }

        // Slot 13 — spyglass: spectate
        int active = plugin.duelMatches() != null ? plugin.duelMatches().all().size() : 0;
        inventory.setItem(13, GUIUtil.item(Material.SPYGLASS,
                "<aqua><bold>SPECTATE</bold></aqua>",
                "<gray>Voir un match en cours</gray>",
                "<dark_gray>Matchs actifs : " + active + "</dark_gray>"));

        // Slot 15 — book: stats
        DuelRewardManager.DuelStats st = plugin.duelRewards() != null
                ? plugin.duelRewards().statsOf(viewer.getUniqueId())
                : new DuelRewardManager.DuelStats(viewer.getUniqueId(), viewer.getName(), 1000, 0, 0, 0, 0);
        inventory.setItem(15, GUIUtil.item(Material.BOOK,
                "<gold><bold>STATISTICS</bold></gold>",
                "<gray>ELO: <white>" + st.elo() + "</white></gray>",
                "<gray>Wins: <green>" + st.wins() + "</green></gray>",
                "<gray>Losses: <red>" + st.losses() + "</red></gray>",
                "<gray>Streak: <gold>" + st.streak() + "</gold></gray>"));

        // Slot 17 — wait time / queue size info
        int qsize = plugin.duelQueue() != null ? plugin.duelQueue().size() : 0;
        inventory.setItem(17, GUIUtil.item(Material.CLOCK,
                "<aqua><bold>WAIT TIME</bold></aqua>",
                "<gray>En file : <white>" + qsize + "</white> joueur(s)</gray>",
                "<gray>Estimation : <white>" + estimateWait(qsize) + "</white></gray>"));
    }

    private String estimateWait(int qsize) {
        if (qsize < 2) return "calcul...";
        // Rough heuristic — average paring tick is 1s with no preferences.
        return "<10s";
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player p = (Player) event.getWhoClicked();
        switch (slot) {
            case 11 -> {
                if (plugin.duelMatches() != null && plugin.duelMatches().byPlayer(p.getUniqueId()) != null) {
                    return; // already in match
                }
                if (plugin.duelQueue() == null) return;
                if (plugin.duelQueue().isQueued(p.getUniqueId())) {
                    plugin.duelQueue().leave(p);
                } else {
                    plugin.duelQueue().enqueue(p, null);
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        // Refresh by re-rendering instead of opening a new inventory
                        // (avoids the close/open flicker).
                        render();
                    }
                }, 2L);
            }
            case 13 -> {
                p.closeInventory();
                DuelSpectateGUI.open(plugin, p);
            }
            case 15 -> {
                // Stats are already rendered in the lore — keep this as a
                // refresh action so the player can re-read them after a match.
                render();
            }
            case 17 -> render();
            default -> { /* border */ }
        }
    }
}
