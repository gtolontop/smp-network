package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.WaypointManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoints GUI — two scopes (solo + team). Left click = navigate,
 * right click = re-set to current location, shift-click = delete (5s
 * confirm). A "➕ Créer" tile prompts for a name and captures the
 * caller's current coords.
 */
public class WaypointsGUI extends GUIHolder {

    private final SMPCore plugin;
    private WaypointManager.Kind kind = WaypointManager.Kind.SOLO;

    private static final int SLOT_SOLO_TAB = 3;
    private static final int SLOT_TEAM_TAB = 5;
    private static final int SLOT_CREATE = 45;

    private final Map<Integer, String> slotName = new HashMap<>();
    private final Map<Integer, Long> pendingDeletes = new HashMap<>();

    public WaypointsGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        open(viewer, WaypointManager.Kind.SOLO);
    }

    public void open(Player viewer, WaypointManager.Kind kind) {
        this.kind = kind;
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#8fd3f4:#c7a7f0><bold>Waypoints</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);
        slotName.clear();

        inv.setItem(SLOT_SOLO_TAB, GUIUtil.item(
                kind == WaypointManager.Kind.SOLO ? Material.NETHER_STAR : Material.FEATHER,
                (kind == WaypointManager.Kind.SOLO ? "<yellow><bold>" : "<gray>") + "Solo</>",
                "<gray>Waypoints privés.</gray>"));
        inv.setItem(SLOT_TEAM_TAB, GUIUtil.item(
                kind == WaypointManager.Kind.TEAM ? Material.NETHER_STAR : Material.WHITE_BANNER,
                (kind == WaypointManager.Kind.TEAM ? "<yellow><bold>" : "<gray>") + "Team</>",
                "<gray>Partagés avec ta team.</gray>"));

        String ownerId = ownerIdFor(viewer, kind);
        if (ownerId == null) {
            inv.setItem(22, GUIUtil.item(Material.BARRIER,
                    "<red>Pas de team</red>",
                    "<gray>Rejoins une team pour utiliser cet onglet.</gray>"));
        } else {
            List<WaypointManager.Waypoint> all = plugin.waypoints().list(kind, ownerId);
            int[] slots = innerSlots(6);
            for (int i = 0; i < all.size() && i < slots.length; i++) {
                WaypointManager.Waypoint w = all.get(i);
                long pending = pendingDeletes.getOrDefault(slots[i], 0L);
                boolean confirming = pending > System.currentTimeMillis();
                Material icon = confirming ? Material.TNT : Material.COMPASS;
                String title = confirming
                        ? "<red><bold>⚠ Confirmer suppression</bold></red>"
                        : "<aqua><bold>" + w.name() + "</bold></aqua>";
                inv.setItem(slots[i], GUIUtil.item(icon, title,
                        "<gray>Serveur: <white>" + w.server() + "</white></gray>",
                        "<gray>Monde: <white>" + w.world() + "</white></gray>",
                        "<gray>Coords: <white>" + (int) w.x() + ", " + (int) w.y() + ", " + (int) w.z() + "</white></gray>",
                        "",
                        "<green>▶ Clic gauche: Afficher les coords</green>",
                        "<yellow>▶ Clic droit: Redéfinir ici</yellow>",
                        "<red>▶ Shift-clic: Supprimer</red>"));
                slotName.put(slots[i], w.name());
            }
            inv.setItem(SLOT_CREATE, GUIUtil.item(Material.EMERALD,
                    "<green><bold>+ Nouveau waypoint</bold></green>",
                    "<gray>Enregistre ta position actuelle.</gray>"));
        }

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private int[] innerSlots(int rows) {
        int[] out = new int[(rows - 3) * 7];
        int idx = 0;
        for (int r = 2; r < rows - 1; r++) {
            for (int c = 1; c < 8; c++) out[idx++] = r * 9 + c;
        }
        return out;
    }

    private String ownerIdFor(Player p, WaypointManager.Kind kind) {
        if (kind == WaypointManager.Kind.SOLO) return p.getUniqueId().toString();
        PlayerData d = plugin.players().get(p);
        return d != null ? d.teamId() : null;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();

        if (raw == SLOT_SOLO_TAB) { open(p, WaypointManager.Kind.SOLO); return; }
        if (raw == SLOT_TEAM_TAB) { open(p, WaypointManager.Kind.TEAM); return; }

        if (raw == SLOT_CREATE) {
            String ownerId = ownerIdFor(p, kind);
            if (ownerId == null) { p.sendMessage(Msg.err("Pas de team.")); return; }
            if (kind == WaypointManager.Kind.TEAM) {
                PlayerData d = plugin.players().get(p);
                var t = d != null ? plugin.teams().get(d.teamId()) : null;
                if (t == null || !t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner uniquement pour un waypoint de team."));
                    return;
                }
            }
            p.closeInventory();
            plugin.chatPrompt().ask(p, "<aqua>Nom du waypoint (3-24 chars) :</aqua>", 30, name -> {
                if (name == null || name.isBlank() || name.length() > 24 || name.length() < 3) {
                    p.sendMessage(Msg.err("Nom invalide.")); return;
                }
                String oid = ownerIdFor(p, kind);
                if (oid == null) { p.sendMessage(Msg.err("Tu n'es plus éligible.")); return; }
                if (plugin.waypoints().get(kind, oid, name) != null) {
                    p.sendMessage(Msg.err("Ce nom existe déjà.")); return;
                }
                plugin.waypoints().set(kind, oid, name, p.getLocation(), null);
                p.sendMessage(Msg.ok("<green>Waypoint <aqua>" + name + "</aqua> créé.</green>"));
            });
            return;
        }

        String name = slotName.get(raw);
        if (name == null) return;
        String ownerId = ownerIdFor(p, kind);
        if (ownerId == null) return;
        WaypointManager.Waypoint w = plugin.waypoints().get(kind, ownerId, name);
        if (w == null) return;

        if (event.getClick().isShiftClick()) {
            if (kind == WaypointManager.Kind.TEAM) {
                PlayerData d = plugin.players().get(p);
                var t = d != null ? plugin.teams().get(d.teamId()) : null;
                if (t == null || !t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner uniquement.")); return;
                }
            }
            long now = System.currentTimeMillis();
            Long pending = pendingDeletes.get(raw);
            if (pending == null || pending <= now) {
                pendingDeletes.clear();
                pendingDeletes.put(raw, now + 5000);
                open(p, kind);
                p.sendMessage(Msg.info("<yellow>Shift-clic à nouveau dans 5s pour confirmer.</yellow>"));
                return;
            }
            plugin.waypoints().delete(kind, ownerId, name);
            pendingDeletes.remove(raw);
            open(p, kind);
            p.sendMessage(Msg.ok("<red>Waypoint supprimé.</red>"));
            return;
        }

        if (event.getClick() == ClickType.RIGHT) {
            if (kind == WaypointManager.Kind.TEAM) {
                PlayerData d = plugin.players().get(p);
                var t = d != null ? plugin.teams().get(d.teamId()) : null;
                if (t == null || !t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner uniquement.")); return;
                }
            }
            plugin.waypoints().set(kind, ownerId, name, p.getLocation(), w.color());
            open(p, kind);
            p.sendMessage(Msg.ok("<yellow>Waypoint redéfini.</yellow>"));
            return;
        }

        // Left click = show coords
        showCoords(p, w);
    }

    public void showCoords(Player p, WaypointManager.Waypoint w) {
        p.closeInventory();
        p.sendMessage(Msg.mm("<aqua><bold>" + w.name() + "</bold></aqua>"));
        p.sendMessage(Msg.mm("<gray>Serveur: <white>" + w.server() + "</white></gray>"));
        p.sendMessage(Msg.mm("<gray>Monde: <white>" + w.world() + "</white></gray>"));
        p.sendMessage(Msg.mm("<gray>Coords: <white>"
                + (int) w.x() + ", " + (int) w.y() + ", " + (int) w.z() + "</white></gray>"));
    }
}
