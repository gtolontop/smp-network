package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import fr.smp.core.voidstone.VoidstoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VoidstoneCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public VoidstoneCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refused."));
            return true;
        }

        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) {
            sender.sendMessage(Msg.err("Voidstones are not active on this server."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Msg.info("<gray>Usage: /voidstone give <player> [amount]</gray>"));
            sender.sendMessage(Msg.info("<gray>       /voidstone materials</gray>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.err("Usage: /voidstone give <player> [amount]"));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Msg.err("Player not found."));
                    return true;
                }

                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Msg.err("Invalid amount."));
                        return true;
                    }
                }

                for (int i = 0; i < amount; i++) {
                    ItemStack item = manager.createItem();
                    Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
                    overflow.values().forEach(stack ->
                            target.getWorld().dropItemNaturally(target.getLocation(), stack));
                }

                sender.sendMessage(Msg.ok("<green>Gave <yellow>x" + amount + "</yellow> voidstone(s) to "
                        + target.getName() + ".</green>"));
                if (!sender.equals(target)) {
                    target.sendMessage(Msg.info("<green>You received a <yellow>Voidstone</yellow>.</green>"));
                }
                return true;
            }
            case "materials" -> {
                String joined = String.join(", ", manager.allowedMaterials().stream()
                        .map(VoidstoneManager::prettyName)
                        .toList());
                sender.sendMessage(Msg.info("<gray>Supported materials: <yellow>" + joined + "</yellow></gray>"));
                return true;
            }
            default -> {
                sender.sendMessage(Msg.err("Unknown subcommand."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("give", "materials")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (prefix.isEmpty() || player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }
}
