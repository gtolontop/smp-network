package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GiveCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public GiveCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /give <joueur> <item> [quantité]"));
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Msg.err("Joueur introuvable : <white>" + args[0] + "</white>"));
            return true;
        }

        Material material = Material.matchMaterial(args[1]);
        if (material == null || material.isAir() || !material.isItem()) {
            sender.sendMessage(Msg.err("Item inconnu : <white>" + args[1] + "</white>"));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Msg.err("Quantité invalide."));
                return true;
            }
            if (amount < 1 || amount > 2304) {
                sender.sendMessage(Msg.err("Quantité invalide (1–2304)."));
                return true;
            }
        }

        int maxStack = material.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            target.getInventory().addItem(new ItemStack(material, give));
            remaining -= give;
        }

        String itemName = material.name().toLowerCase().replace('_', ' ');
        sender.sendMessage(Msg.ok("<gray>Donné <aqua>" + amount + "× " + itemName
                + "</aqua> à <aqua>" + target.getName() + "</aqua>.</gray>"));
        if (target != sender) {
            String senderName = sender instanceof Player p ? p.getName() : "Console";
            target.sendMessage(Msg.ok("<gray><aqua>" + senderName + "</aqua> t'a donné <aqua>"
                    + amount + "× " + itemName + "</aqua>.</gray>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) {
            var out = new ArrayList<String>();
            String pref = args[0].toLowerCase();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
            return out;
        }
        if (args.length == 2) {
            var out = new ArrayList<String>();
            String pref = args[1].toLowerCase();
            for (Material m : Material.values()) {
                if (!m.isItem() || m.isAir() || m.isLegacy()) continue;
                String name = m.name().toLowerCase();
                if (name.startsWith(pref)) out.add(name);
                if (out.size() >= 50) break;
            }
            return out;
        }
        return List.of();
    }
}
