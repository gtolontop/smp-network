package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class RtpGUI extends GUIHolder {

    private final SMPCore plugin;

    public RtpGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 27,
                GUIUtil.title("<gradient:#a8edea:#fed6e3><bold>Random Teleport</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.CYAN_STAINED_GLASS_PANE);

        long cd = plugin.rtp().cooldownLeft(p);
        String cdLine = cd > 0
                ? "<red>Cooldown <white>" + Msg.duration(cd) + "</white></red>"
                : "<green>Prêt</green>";

        inv.setItem(11, GUIUtil.item(Material.GRASS_BLOCK,
                "<green><bold>Overworld</bold></green>",
                "",
                "<gray>Téléporte aléatoirement dans l'overworld.</gray>",
                "",
                cdLine,
                "",
                "<yellow>▶ Clic pour partir</yellow>"));

        inv.setItem(13, GUIUtil.item(Material.NETHERRACK,
                "<red><bold>Nether</bold></red>",
                "",
                "<gray>Téléporte aléatoirement dans le Nether.</gray>",
                "",
                cdLine,
                "",
                "<yellow>▶ Clic pour partir</yellow>"));

        boolean endOn = plugin.endToggle().enabled();
        if (endOn) {
            inv.setItem(15, GUIUtil.item(Material.END_STONE,
                    "<light_purple><bold>End</bold></light_purple>",
                    "",
                    "<gray>Téléporte aléatoirement dans l'End.</gray>",
                    "",
                    cdLine,
                    "",
                    "<yellow>▶ Clic pour partir</yellow>"));
        } else {
            inv.setItem(15, GUIUtil.item(Material.BARRIER,
                    "<dark_gray><bold>End</bold></dark_gray>",
                    "",
                    "<red>L'End est désactivé.</red>"));
        }

        inv.setItem(22, GUIUtil.item(Material.BARRIER,
                "<red>Fermer</red>"));

        this.inventory = inv;
        p.openInventory(inv);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();
        World target = null;
        String suffix = plugin.getConfig().getString("rtp.world-overworld", "world");
        switch (slot) {
            case 11 -> target = plugin.resolveWorld(suffix, World.Environment.NORMAL);
            case 13 -> target = plugin.resolveWorld(
                    plugin.getConfig().getString("rtp.world-nether", suffix + "_nether"),
                    World.Environment.NETHER);
            case 15 -> {
                if (!plugin.endToggle().enabled()) {
                    p.sendMessage(Msg.err("<red>L'End est désactivé.</red>"));
                    return;
                }
                target = plugin.resolveWorld(
                        plugin.getConfig().getString("rtp.world-end", suffix + "_the_end"),
                        World.Environment.THE_END);
            }
            case 22 -> { p.closeInventory(); return; }
            default -> { return; }
        }
        p.closeInventory();
        String worldName;
        switch (slot) {
            case 11 -> worldName = plugin.getConfig().getString("rtp.world-overworld", "world");
            case 13 -> worldName = plugin.getConfig().getString("rtp.world-nether", "world_nether");
            case 15 -> worldName = plugin.getConfig().getString("rtp.world-end", "world_the_end");
            default -> { return; }
        }

        // Cross-server: if we're not on survival, persist RTP intent + transfer.
        if (!plugin.isMainSurvival()) {
            plugin.pendingTp().set(p.getUniqueId(),
                    new fr.smp.core.managers.PendingTeleportManager.Pending(
                            fr.smp.core.managers.PendingTeleportManager.Kind.RTP,
                            worldName, 0, 0, 0, 0, 0,
                            System.currentTimeMillis(), "survival"));
            p.sendMessage(Msg.info("<aqua>Transfert vers survie pour RTP...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, "survival");
            return;
        }

        if (target == null) {
            p.sendMessage(Msg.err("Ce monde n'est pas chargé."));
            return;
        }
        long cd = plugin.rtp().cooldownLeft(p);
        if (cd > 0) {
            p.sendMessage(Msg.err("Cooldown: <white>" + Msg.duration(cd) + "</white>"));
            return;
        }
        p.sendMessage(Msg.info("<aqua>Recherche d'un lieu sûr...</aqua>"));
        plugin.rtp().teleport(p, target);
    }
}
