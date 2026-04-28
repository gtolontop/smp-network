package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.GUIHolder;
import fr.smp.core.gui.GUIUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every running match: each item is a paper showing the two duelers,
 * click to spectate. A diamond in slot 0 picks a random ongoing match.
 */
public final class DuelSpectateGUI extends GUIHolder {

    private static final int SIZE = 54;

    private final SMPCore plugin;
    private final Player viewer;
    private final List<Long> matchIds = new ArrayList<>();

    private DuelSpectateGUI(SMPCore plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public static void open(SMPCore plugin, Player viewer) {
        DuelSpectateGUI g = new DuelSpectateGUI(plugin, viewer);
        Inventory inv = Bukkit.createInventory(g, SIZE,
                GUIUtil.title("<dark_gray>» <gold>Spectate</gold> <gray>« Matchs en cours"));
        g.inventory = inv;
        g.render();
        viewer.openInventory(inv);
    }

    private void render() {
        if (plugin.duelMatches() == null) return;
        inventory.setItem(0, GUIUtil.item(Material.DIAMOND,
                "<aqua><bold>RANDOM</bold></aqua>",
                "<gray>Choisit un match au hasard</gray>"));

        int slot = 9; // skip the first row, reserved for filters/random
        for (DuelMatch m : plugin.duelMatches().all()) {
            if (slot >= SIZE) break;
            matchIds.add(m.id());
            inventory.setItem(slot, GUIUtil.item(Material.PAPER,
                    "<white><bold>" + m.nameA() + "</bold></white> <gray>vs</gray> <white><bold>" + m.nameB() + "</bold></white>",
                    "<gray>Arène: <white>" + m.arena().name() + "</white></gray>",
                    "<gray>État: <white>" + m.state() + "</white></gray>",
                    "<gray>Spectateurs: <white>" + m.spectators().size() + "</white></gray>",
                    "",
                    "<aqua>Click pour spectate</aqua>"));
            slot++;
        }
        if (slot == 9) {
            inventory.setItem(22, GUIUtil.item(Material.BARRIER,
                    "<red>Aucun match en cours</red>",
                    "<gray>Reviens dans quelques secondes.</gray>"));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player p = (Player) event.getWhoClicked();
        if (slot == 0) {
            // Random pick
            DuelMatch m = plugin.duelMatches().all().stream().findFirst().orElse(null);
            if (m == null) return;
            doSpectate(p, m);
            return;
        }
        if (slot < 9 || slot >= SIZE) return;
        int idx = slot - 9;
        if (idx < 0 || idx >= matchIds.size()) return;
        DuelMatch m = plugin.duelMatches().byId(matchIds.get(idx));
        if (m == null) {
            p.closeInventory();
            return;
        }
        doSpectate(p, m);
    }

    private void doSpectate(Player p, DuelMatch m) {
        p.closeInventory();
        if (m.world() == null) return;
        m.spectators().add(p.getUniqueId());
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        p.teleportAsync(m.arena().center().clone()
                .toVector().toLocation(m.world(), 0f, 0f));
    }
}
