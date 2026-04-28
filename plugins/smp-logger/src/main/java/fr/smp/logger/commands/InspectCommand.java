package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.query.LookupEngine;
import fr.smp.logger.util.RowFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /inspect toggles a per-player flag. While active, left-click block returns
 * its 7d history, right-click container returns container access history.
 */
public class InspectCommand implements CommandExecutor, Listener {

    private final SMPLogger plugin;
    private final LookupEngine engine;
    private final RowFormatter fmt;
    private final Set<UUID> active = new HashSet<>();

    public InspectCommand(SMPLogger plugin) {
        this.plugin = plugin;
        this.engine = new LookupEngine(plugin);
        this.fmt = new RowFormatter(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (active.remove(p.getUniqueId())) {
            p.sendMessage(Component.text("Inspect mode: OFF", NamedTextColor.RED));
        } else {
            active.add(p.getUniqueId());
            p.sendMessage(Component.text("Inspect mode: ON — click any block to see its history", NamedTextColor.GREEN));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent e) {
        if (!active.contains(e.getPlayer().getUniqueId())) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        int wid = plugin.worlds().idOf(b.getWorld());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<LookupEngine.Row> rows = engine.blockHistory(wid, b.getX(), b.getY(), b.getZ(), 32);
            if (rows.isEmpty()) {
                p.sendMessage(Component.text("No history at " + b.getX() + "," + b.getY() + "," + b.getZ(), NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text("─── " + b.getType() + " @ " + b.getX() + "," + b.getY() + "," + b.getZ()
                    + " (" + rows.size() + " events) ───", NamedTextColor.GOLD));
            for (LookupEngine.Row r : rows) p.sendMessage(fmt.format(r));
        });
    }
}
