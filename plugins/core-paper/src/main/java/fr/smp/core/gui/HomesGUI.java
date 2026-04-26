package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.HomeManager;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Homes GUI — 6 rows. Row 1 = banner + home row, rows 2-5 = gray-glass area.
 * Under each home the gray pane becomes a red delete button when a home exists.
 */
public class HomesGUI extends GUIHolder {

    private static final int SLOT_TEAM_BANNER  = 10;
    private static final int SLOT_SPACER       = 11;
    private static final int[] BED_SLOTS       = {12, 13, 14, 15, 16};
    private static final int[] DELETE_SLOTS    = {21, 22, 23, 24, 25};

    public static final int MAX_HOMES = BED_SLOTS.length;

    private final SMPCore plugin;
    private final Player viewer;
    private final int maxSlots;

    private final Map<Integer, Long> pendingDeletes = new HashMap<>();

    public HomesGUI(SMPCore plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.maxSlots = Math.min(MAX_HOMES, plugin.homes().maxSlots(viewer));
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#67e8f9:#a78bfa><bold>Homes</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // Gray-glass filler for the 4 rows below the home row (rows 2..5).
        for (int row = 2; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                inv.setItem(row * 9 + col, GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        // Spacer slot stays empty between banner and home row.
        inv.setItem(SLOT_SPACER, null);

        PlayerData d = plugin.players().get(viewer);
        TeamManager.Team team = d != null && d.teamId() != null ? plugin.teams().get(d.teamId()) : null;

        if (team != null && team.home() != null) {
            inv.setItem(SLOT_TEAM_BANNER, GUIUtil.item(Material.WHITE_BANNER,
                    team.color() + "<bold>Team Home</bold><reset>",
                    "<gray>" + team.color() + "[" + team.tag() + "]<reset></gray>",
                    "",
                    "<yellow>▶ Clic gauche: Téléport</yellow>",
                    team.owner().equals(viewer.getUniqueId().toString())
                            ? "<yellow>▶ Clic droit: Redéfinir ici</yellow>"
                            : "<dark_gray>(owner uniquement pour définir)</dark_gray>",
                    team.owner().equals(viewer.getUniqueId().toString())
                            ? "<red>▶ Shift-clic: Supprimer</red>"
                            : ""));
        } else if (team != null) {
            inv.setItem(SLOT_TEAM_BANNER, GUIUtil.item(Material.GRAY_BANNER,
                    "<dark_gray><bold>Team Home</bold></dark_gray>",
                    "<gray>Aucun home défini.</gray>",
                    "",
                    team.owner().equals(viewer.getUniqueId().toString())
                            ? "<yellow>▶ Clic: Définir ici</yellow>"
                            : "<dark_gray>(owner uniquement)</dark_gray>"));
        } else {
            inv.setItem(SLOT_TEAM_BANNER, GUIUtil.item(Material.GRAY_BANNER,
                    "<dark_gray><bold>Pas de team</bold></dark_gray>",
                    "<gray>/team pour en créer une.</gray>"));
        }

        Map<Integer, HomeManager.Home> list = plugin.homes().list(viewer.getUniqueId());

        for (int i = 0; i < BED_SLOTS.length; i++) {
            int slot = i + 1;
            int bedSlot = BED_SLOTS[i];
            int deleteSlot = DELETE_SLOTS[i];
            if (slot > maxSlots) {
                inv.setItem(bedSlot, GUIUtil.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "<dark_gray><bold>Slot " + slot + "</bold></dark_gray>",
                        "<gray>Verrouillé (permission).</gray>"));
                continue;
            }
            HomeManager.Home home = list.get(slot);
            if (home == null) {
                inv.setItem(bedSlot, GUIUtil.item(Material.LIGHT_GRAY_DYE,
                        "<gray><bold>Slot " + slot + "</bold></gray>",
                        "<gray>Slot libre.</gray>",
                        "",
                        "<green>▶ Clic gauche: Définir ici</green>"));
                continue;
            }
            long pending = pendingDeletes.getOrDefault(bedSlot, 0L);
            long pendingDel = pendingDeletes.getOrDefault(deleteSlot, 0L);
            boolean confirming = pending > System.currentTimeMillis()
                    || pendingDel > System.currentTimeMillis();
            Material bed = bedByIndex(i);
            if (confirming) {
                inv.setItem(bedSlot, GUIUtil.item(Material.TNT,
                        "<red><bold>⚠ Confirmer suppression</bold></red>",
                        "<gray>Home " + slot + "</gray>",
                        "",
                        "<red>▶ Shift-clic ou bouton rouge: Confirmer</red>"));
            } else {
                inv.setItem(bedSlot, GUIUtil.item(bed,
                        "<aqua><bold>Home " + slot + "</bold></aqua>",
                        "",
                        "<green>▶ Clic gauche: Téléport</green>",
                        "<yellow>▶ Clic droit: Redéfinir</yellow>",
                        "<red>▶ Shift-clic: Supprimer</red>"));
            }
            inv.setItem(deleteSlot, GUIUtil.item(
                    confirming ? Material.RED_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    "<red><bold>✖ Supprimer Home " + slot + "</bold></red>",
                    "",
                    confirming
                            ? "<red>▶ Clic: Confirmer la suppression</red>"
                            : "<yellow>▶ Clic: Supprimer ce home</yellow>"));
        }

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private Material bedByIndex(int i) {
        Material[] beds = {Material.BLUE_BED, Material.CYAN_BED, Material.LIGHT_BLUE_BED,
                Material.LIME_BED, Material.GREEN_BED, Material.YELLOW_BED,
                Material.ORANGE_BED, Material.RED_BED, Material.PINK_BED,
                Material.MAGENTA_BED, Material.PURPLE_BED, Material.BLACK_BED};
        return beds[i % beds.length];
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        ClickType click = event.getClick();

        if (raw == SLOT_TEAM_BANNER) {
            handleTeamBanner(p, click);
            return;
        }
        for (int i = 0; i < BED_SLOTS.length; i++) {
            if (BED_SLOTS[i] != raw) continue;
            handleBed(p, i, click);
            return;
        }
        for (int i = 0; i < DELETE_SLOTS.length; i++) {
            if (DELETE_SLOTS[i] != raw) continue;
            handleDeleteButton(p, i);
            return;
        }
    }

    private void handleTeamBanner(Player p, ClickType click) {
        PlayerData d = plugin.players().get(p);
        if (d == null || d.teamId() == null) return;
        TeamManager.Team t = plugin.teams().get(d.teamId());
        if (t == null) return;
        boolean owner = t.owner().equals(p.getUniqueId().toString());

        if (click.isShiftClick()) {
            if (!owner) { p.sendMessage(Msg.err("Owner uniquement.")); return; }
            if (t.home() == null) return;
            if (!tryConfirm(SLOT_TEAM_BANNER)) { open(); return; }
            plugin.teams().setHome(t.id(), null);
            p.sendMessage(Msg.ok("<red>Home de team supprimé.</red>"));
            open();
            return;
        }
        if (click.isRightClick() || t.home() == null) {
            if (!owner) { p.sendMessage(Msg.err("Owner uniquement.")); return; }
            plugin.teams().setHome(t.id(), p.getLocation());
            p.sendMessage(Msg.ok("<green>Home de team défini ici.</green>"));
            open();
            return;
        }
        p.closeInventory();
        p.teleportAsync(t.home());
        p.sendMessage(Msg.ok("<aqua>Téléporté au home de team.</aqua>"));
    }

    private void handleBed(Player p, int index, ClickType click) {
        int slot = index + 1;
        if (slot > maxSlots) { p.sendMessage(Msg.err("Slot non débloqué.")); return; }
        HomeManager.Home home = plugin.homes().get(p.getUniqueId(), slot);

        if (click.isShiftClick()) {
            if (home == null) return;
            if (!tryConfirm(BED_SLOTS[index])) {
                open();
                p.sendMessage(Msg.info("<yellow>Shift-clic à nouveau dans 5s pour confirmer.</yellow>"));
                return;
            }
            plugin.homes().delete(p.getUniqueId(), slot);
            p.sendMessage(Msg.ok("<red>Home " + slot + " supprimé.</red>"));
            open();
            return;
        }
        if (home == null) {
            if (click.isLeftClick()) {
                plugin.homes().set(p.getUniqueId(), slot, p.getLocation());
                p.sendMessage(Msg.ok("<green>Home <yellow>" + slot + "</yellow> défini.</green>"));
                open();
            }
            return;
        }
        if (click.isRightClick()) {
            plugin.homes().set(p.getUniqueId(), slot, p.getLocation());
            p.sendMessage(Msg.ok("<yellow>Home " + slot + " redéfini.</yellow>"));
            open();
            return;
        }
        if (click.isLeftClick()) {
            p.closeInventory();
            String homeServer = home.server();
            if (homeServer != null && !homeServer.equalsIgnoreCase(plugin.getServerType())) {
                plugin.pendingTp().set(p.getUniqueId(),
                        new fr.smp.core.managers.PendingTeleportManager.Pending(
                                fr.smp.core.managers.PendingTeleportManager.Kind.LOC,
                                home.world(), home.x(), home.y(), home.z(),
                                home.yaw(), home.pitch(),
                                System.currentTimeMillis(), homeServer));
                p.sendMessage(Msg.info("<aqua>Transfert vers <white>" + homeServer + "</white>...</aqua>"));
                plugin.getMessageChannel().sendTransfer(p, homeServer);
                return;
            }
            Location loc = home.toLocation();
            if (loc == null) {
                p.sendMessage(Msg.err("Le monde du home n'est pas chargé."));
                return;
            }
            p.teleportAsync(loc);
            p.sendMessage(Msg.ok("<aqua>Téléporté à home " + slot + ".</aqua>"));
        }
    }

    private void handleDeleteButton(Player p, int index) {
        int slot = index + 1;
        if (slot > maxSlots) return;
        HomeManager.Home home = plugin.homes().get(p.getUniqueId(), slot);
        if (home == null) return;
        if (!tryConfirm(DELETE_SLOTS[index])) {
            open();
            p.sendMessage(Msg.info("<yellow>Reclique dans 5s pour confirmer la suppression.</yellow>"));
            return;
        }
        plugin.homes().delete(p.getUniqueId(), slot);
        p.sendMessage(Msg.ok("<red>Home " + slot + " supprimé.</red>"));
        open();
    }

    /** First click sets pending; second click within 5s passes. */
    private boolean tryConfirm(int slot) {
        long now = System.currentTimeMillis();
        Long pending = pendingDeletes.get(slot);
        if (pending != null && pending > now) {
            pendingDeletes.remove(slot);
            return true;
        }
        pendingDeletes.clear();
        pendingDeletes.put(slot, now + 5000);
        return false;
    }
}
