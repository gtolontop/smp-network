package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /find GUI — restricted to the caller's teammates (admins bypass).
 */
public class FindGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final Map<Integer, UUID> slotPlayers = new HashMap<>();

    public FindGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        PlayerData vd = plugin.players().get(viewer);
        String teamId = vd != null ? vd.teamId() : null;
        boolean admin = viewer.hasPermission("smp.admin");

        // Collect visible targets: teammates only (admin sees everyone).
        List<Player> visible = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (admin) { visible.add(other); continue; }
            if (teamId == null) continue;
            PlayerData od = plugin.players().get(other);
            if (od != null && teamId.equals(od.teamId())) visible.add(other);
        }

        int rows = Math.max(3, Math.min(6, (visible.size() / 7) + 3));
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(this, size,
                GUIUtil.title("<gradient:#ff9a9e:#fad0c4><bold>Équipe en ligne</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.PINK_STAINED_GLASS_PANE);

        slotPlayers.clear();
        int[] inner = innerSlots(rows);
        int count = 0;
        for (Player other : visible) {
            if (count >= inner.length) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(other);
            meta.displayName(MM.deserialize("<!italic><yellow><bold>" + other.getName() + "</bold></yellow>"));
            PlayerData d = plugin.players().get(other);
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (d != null) {
                lore.add(MM.deserialize("<!italic><green>$ </green><white>" + Msg.money(d.money()) + "</white>"));
                lore.add(MM.deserialize("<!italic><aqua>◆ </aqua><white>" + d.shards() + " saphirs</white>"));
                lore.add(MM.deserialize("<!italic><red>⚔ </red><white>" + d.kills() + " kills</white>"));
            }
            Location loc = other.getLocation();
            lore.add(MM.deserialize("<!italic><gray>" + loc.getWorld().getName() + " " +
                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gray>"));
            lore.add(MM.deserialize("<!italic> "));
            lore.add(MM.deserialize("<!italic><yellow>▶ Clic pour demander /tpa</yellow>"));
            meta.lore(lore);
            head.setItemMeta(meta);
            inv.setItem(inner[count], head);
            slotPlayers.put(inner[count], other.getUniqueId());
            count++;
        }

        if (visible.isEmpty()) {
            inv.setItem(size / 2, GUIUtil.item(Material.BARRIER,
                    "<red><bold>Aucun équipier en ligne</bold></red>",
                    "<gray>Seuls les membres de ta team sont visibles ici.</gray>"));
        }

        this.inventory = inv;
        viewer.openInventory(inv);
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
        UUID targetId = slotPlayers.get(event.getRawSlot());
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) return;
        p.closeInventory();
        if (plugin.tpa().send(p, target, fr.smp.core.managers.TpaManager.Type.TO)) {
            p.sendMessage(Msg.ok("<aqua>Demande TPA envoyée à " + target.getName() + ".</aqua>"));
            target.sendMessage(Msg.info("<aqua>" + p.getName() + "</aqua> veut se téléporter chez toi. <green>/tpaccept</green> ou <red>/tpdeny</red>."));
        }
    }
}
