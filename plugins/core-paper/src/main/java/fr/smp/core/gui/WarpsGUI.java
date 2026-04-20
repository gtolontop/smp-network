package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.WarpManager;
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

public class WarpsGUI extends GUIHolder {

    private final SMPCore plugin;
    private final boolean adminMode;
    private final Map<Integer, String> slotWarps = new HashMap<>();
    private final Map<Integer, Long> pendingDeletes = new HashMap<>();

    public WarpsGUI(SMPCore plugin) {
        this(plugin, false);
    }

    public WarpsGUI(SMPCore plugin, boolean adminMode) {
        this.plugin = plugin;
        this.adminMode = adminMode;
    }

    public void open(Player p) {
        boolean admin = adminMode && p.hasPermission("smp.admin");
        List<WarpManager.Warp> warps = plugin.warps().list();
        int rows = Math.max(3, Math.min(6, (warps.size() / 7) + 3));
        int size = rows * 9;

        String title = admin
                ? "<gradient:#ff9a9e:#fad0c4><bold>Warps (Admin)</bold></gradient>"
                : "<gradient:#84fab0:#8fd3f4><bold>Warps</bold></gradient>";
        Inventory inv = Bukkit.createInventory(this, size, GUIUtil.title(title));
        GUIUtil.fillBorder(inv, admin ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE);

        slotWarps.clear();
        int[] inner = innerSlots(rows);
        for (int i = 0; i < warps.size() && i < inner.length; i++) {
            WarpManager.Warp w = warps.get(i);
            String desc = w.description() != null && !w.description().isEmpty()
                    ? w.description() : "<gray>Aucune description.</gray>";
            int slot = inner[i];
            boolean confirming = pendingDeletes.getOrDefault(slot, 0L) > System.currentTimeMillis();
            if (admin && confirming) {
                inv.setItem(slot, GUIUtil.item(Material.TNT,
                        "<red><bold>⚠ Confirmer suppression</bold></red>",
                        "<gray>Warp: <white>" + w.name() + "</white></gray>",
                        "",
                        "<red>▶ Clic: Confirmer</red>"));
            } else if (admin) {
                inv.setItem(slot, GUIUtil.item(w.icon(),
                        "<aqua><bold>" + w.name() + "</bold></aqua>",
                        desc,
                        "",
                        "<gray>" + w.world() + " " +
                                (int) w.x() + ", " + (int) w.y() + ", " + (int) w.z() + "</gray>",
                        "",
                        "<green>▶ Clic gauche: TP</green>",
                        "<yellow>▶ Clic droit: Redéfinir ici</yellow>",
                        "<red>▶ Shift+clic: Supprimer</red>"));
            } else {
                inv.setItem(slot, GUIUtil.item(w.icon(),
                        "<aqua><bold>" + w.name() + "</bold></aqua>",
                        desc,
                        "",
                        "<gray>" + w.world() + "</gray>",
                        "",
                        "<yellow>▶ Clic pour s'y rendre</yellow>"));
            }
            slotWarps.put(slot, w.name());
        }

        this.inventory = inv;
        p.openInventory(inv);
    }

    private int[] innerSlots(int rows) {
        int[] inner = new int[(rows - 2) * 7];
        int idx = 0;
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < 8; c++) inner[idx++] = r * 9 + c;
        }
        return inner;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        String name = slotWarps.get(raw);
        if (name == null) return;
        WarpManager.Warp w = plugin.warps().get(name);
        if (w == null) return;
        ClickType click = event.getClick();
        boolean admin = adminMode && p.hasPermission("smp.admin");

        if (admin && click.isShiftClick()) {
            long now = System.currentTimeMillis();
            Long pending = pendingDeletes.get(raw);
            if (pending != null && pending > now) {
                pendingDeletes.remove(raw);
                plugin.warps().delete(name);
                p.sendMessage(Msg.ok("<red>Warp <aqua>" + name + "</aqua> supprimé.</red>"));
                open(p);
                return;
            }
            pendingDeletes.clear();
            pendingDeletes.put(raw, now + 5000);
            p.sendMessage(Msg.info("<yellow>Shift-clic à nouveau dans les 5s pour confirmer.</yellow>"));
            open(p);
            return;
        }
        if (admin && click.isRightClick()) {
            plugin.warps().set(name, p.getLocation(), w.icon(), w.description(), p.getUniqueId());
            p.sendMessage(Msg.ok("<yellow>Warp <aqua>" + name + "</aqua> redéfini ici.</yellow>"));
            open(p);
            return;
        }
        p.closeInventory();
        if (w.server() != null && !w.server().equalsIgnoreCase(plugin.getServerType())) {
            plugin.pendingTp().set(p.getUniqueId(),
                    new fr.smp.core.managers.PendingTeleportManager.Pending(
                            fr.smp.core.managers.PendingTeleportManager.Kind.LOC,
                            w.world(), w.x(), w.y(), w.z(), w.yaw(), w.pitch(),
                            System.currentTimeMillis()));
            p.sendMessage(Msg.info("<aqua>Transfert vers <white>" + w.server() + "</white>...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, w.server());
            return;
        }
        var loc = w.toLocation();
        if (loc == null) {
            p.sendMessage(Msg.err("Monde du warp non chargé."));
            return;
        }
        p.teleportAsync(loc);
        p.sendMessage(Msg.ok("<aqua>Warp → " + name + "</aqua>"));
    }
}
