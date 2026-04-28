package fr.smp.core.duels;

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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Setup wand (golden hoe "Duel Arena Setup") + per-world block protection. The
 * protection rules apply both to template worlds (admin sandbox — but admins
 * bypass) and match worlds (where 2 duelers must stay inside the cylinder).
 *
 * The arena is keyed by world *name* on purpose: when DuelMatchManager copies
 * a template world to a match-instance world, it preserves the same world name
 * (folder is renamed but the world's Bukkit name maps back to the arena), so
 * one lookup serves both.
 */
public class DuelArenaListener implements Listener {

    public static final NamespacedKey WAND_KEY = new NamespacedKey("smpcore", "duel_arena_wand");

    private final SMPCore plugin;

    public DuelArenaListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public static ItemStack createWand() {
        ItemStack it = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Duel Arena Setup", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Clic gauche : centre", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Clic droit  : bord (fixe le rayon)", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("/duelarena create <nom>", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isWand(ItemStack it) {
        if (it == null || it.getType() != Material.GOLDEN_HOE) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(WAND_KEY, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    /**
     * Wand actions: left-click sets the working "center" + floorY (the block
     * the admin clicked), right-click sets the working "edge" — radius is the
     * horizontal distance from center to edge. Persisted into the
     * DuelArenaManager.WandSession (no SQL until /duelarena create).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (!isWand(it)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("smp.admin")) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        DuelArenaCommand.WandSession sess = DuelArenaCommand.session(p);
        Location loc = b.getLocation();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            sess.center = loc.clone().add(0.5, 1.0, 0.5);
            sess.floorY = loc.getBlockY() + 1;
            p.sendMessage(Msg.info("<gold>Centre</gold> <gray>défini à " +
                    loc.getBlockX() + "," + (loc.getBlockY() + 1) + "," + loc.getBlockZ() +
                    " (floor Y=" + (loc.getBlockY() + 1) + ").</gray>"));
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (sess.center == null) {
                p.sendMessage(Msg.err("Pose d'abord le centre (clic gauche)."));
                e.setCancelled(true);
                return;
            }
            double dx = loc.getX() - sess.center.getX();
            double dz = loc.getZ() - sess.center.getZ();
            double r = Math.sqrt(dx * dx + dz * dz);
            r = Math.max(2.0, Math.min(200.0, Math.round(r * 100.0) / 100.0));
            sess.radius = r;
            p.sendMessage(Msg.info("<gold>Rayon</gold> <gray>défini à <white>" + r + "</white>.</gray>"));
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("smp.admin")) return;
        DuelArena a = plugin.duelArenas() == null ? null
                : plugin.duelArenas().byWorldName(e.getBlock().getWorld().getName());
        if (a == null) return;
        if (!isInsideForCombat(a, e.getBlock(), true)) {
            e.setCancelled(true);
            p.sendMessage(Msg.err("Tu ne peux pas casser ce bloc dans cette arène."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("smp.admin")) return;
        DuelArena a = plugin.duelArenas() == null ? null
                : plugin.duelArenas().byWorldName(e.getBlock().getWorld().getName());
        if (a == null) return;
        if (!isInsideForCombat(a, e.getBlock(), false)) {
            e.setCancelled(true);
            p.sendMessage(Msg.err("Tu ne peux pas poser de bloc ici."));
        }
    }

    /**
     * Combat-time bounds:
     *   - placement: must be inside cylinder XZ AND between floorY and floor+ceiling
     *   - break:     must be inside cylinder XZ AND between floor-digDepth and floor+ceiling
     *
     * "Pas casser sur les côtés" → cylinder XZ check.
     * "Casser en profondeur"     → break allowed below floor down to digDepth.
     */
    private boolean isInsideForCombat(DuelArena a, Block b, boolean isBreak) {
        if (!a.withinCylinderXZ(b.getX() + 0.5, b.getZ() + 0.5)) return false;
        int y = b.getY();
        int floor = a.floorY();
        if (isBreak) {
            return y >= floor - a.digDepth() && y < floor + a.ceiling();
        } else {
            return y >= floor && y < floor + a.ceiling();
        }
    }
}
