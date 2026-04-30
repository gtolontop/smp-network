package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.OrderBrowseGUI;
import fr.smp.core.order.OrderSort;
import fr.smp.core.utils.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /order [item|player]   — browse all orders, optionally filtered by item
 * /orders [player]       — browse orders by player (defaults to self)
 *
 * In-GUI sort: the hopper button cycles through the 6 OrderSort values.
 */
public class OrderCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public OrderCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }

        String name = cmd.getName().toLowerCase();

        // /orders [player] — filter by player name
        if (name.equals("orders")) {
            String target = args.length > 0 ? args[0] : p.getName();
            new OrderBrowseGUI(plugin,
                    OrderBrowseGUI.Filter.player(target),
                    OrderSort.DATE_DESC).open(p);
            return true;
        }

        // /order [<item>]
        if (args.length == 0) {
            new OrderBrowseGUI(plugin, OrderBrowseGUI.Filter.all(), OrderSort.DATE_DESC).open(p);
            return true;
        }

        // Try to match the argument as a material name
        String raw = args[0].toUpperCase().replace('-', '_').replace(' ', '_');
        Material mat = Material.matchMaterial(raw);
        if (mat != null) {
            new OrderBrowseGUI(plugin, OrderBrowseGUI.Filter.item(mat), OrderSort.DATE_DESC).open(p);
        } else {
            // Treat it as a player name filter
            new OrderBrowseGUI(plugin, OrderBrowseGUI.Filter.player(args[0]), OrderSort.DATE_DESC).open(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length != 1) return List.of();

        String prefix = args[0].toLowerCase();
        List<String> out = new ArrayList<>();

        String cmdName = cmd.getName().toLowerCase();
        if (cmdName.equals("orders")) {
            // Suggest online player names
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) out.add(online.getName());
            }
        } else {
            // Suggest item names from the picker list
            for (Material m : ORDERABLE_MATERIALS) {
                String key = m.name().toLowerCase();
                if (key.startsWith(prefix)) out.add(m.name().toLowerCase());
            }
            // Also suggest online player names
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) out.add(online.getName());
            }
        }
        return out;
    }

    // Same list as OrderItemPickGUI for tab completion
    private static final Material[] ORDERABLE_MATERIALS = {
        Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT,
        Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP, Material.COAL, Material.COPPER_INGOT,
        Material.LAPIS_LAZULI, Material.AMETHYST_SHARD, Material.QUARTZ,
        Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.GOLD_BLOCK,
        Material.IRON_BLOCK, Material.NETHERITE_BLOCK, Material.COPPER_BLOCK,
        Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
        Material.MELON_SLICE, Material.PUMPKIN, Material.SUGAR_CANE,
        Material.NETHER_WART, Material.COCOA_BEANS,
        Material.STRING, Material.FEATHER, Material.LEATHER, Material.INK_SAC,
        Material.BONE_MEAL, Material.BLAZE_ROD, Material.ENDER_PEARL,
        Material.GHAST_TEAR, Material.MAGMA_CREAM, Material.SLIME_BALL, Material.GUNPOWDER,
        Material.NETHER_STAR, Material.SHULKER_SHELL, Material.ANCIENT_DEBRIS,
        Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS,
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
    };
}
