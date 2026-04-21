package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Wand handling (wooden shovel named "Gate Setup") + block-break protection
 * so non-admin players can't dismantle the gate while it's closed.
 */
public class GateListener implements Listener {

    public static final NamespacedKey WAND_KEY = new NamespacedKey("smpcore", "gate_wand");

    private final SMPCore plugin;

    public GateListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public static ItemStack createWand() {
        ItemStack it = new ItemStack(Material.WOODEN_SHOVEL);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Gate Setup", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Clic gauche : pos1", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Clic droit  : pos2", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("/gate create <nom> [rayon]", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isWand(ItemStack it) {
        if (it == null || it.getType() != Material.WOODEN_SHOVEL) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        Byte flag = meta.getPersistentDataContainer().get(WAND_KEY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (!isWand(it)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("smp.admin")) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        Location loc = b.getLocation();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.gates().setPos1(p, loc);
            p.sendMessage(Msg.info("<aqua>Pos1</aqua> définie <gray>(" +
                    loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")</gray>"));
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.gates().setPos2(p, loc);
            p.sendMessage(Msg.info("<aqua>Pos2</aqua> définie <gray>(" +
                    loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")</gray>"));
            e.setCancelled(true);
        }
    }

    /**
     * Non-admins can't break gate blocks (so the grid isn't farmable for resources).
     * Admins go through — they're the ones doing setup / maintenance.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("smp.admin")) return;
        if (plugin.gates().isProtectedBlock(e.getBlock())) {
            e.setCancelled(true);
            p.sendMessage(Msg.err("Bloc protégé (gate)."));
        }
    }
}
