package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RepairCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public RepairCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("all")) {
            Player target;
            if (args.length >= 2) {
                target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Msg.err("Joueur introuvable."));
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage("Joueurs uniquement.");
                return true;
            }
            int repaired = repairAll(target);
            if (repaired == 0) {
                if (target == sender) {
                    sender.sendMessage(Msg.err("Aucun item à réparer dans ton inventaire."));
                } else {
                    sender.sendMessage(Msg.err("Aucun item à réparer dans l'inventaire de <aqua>" + target.getName() + "</aqua>."));
                }
                return true;
            }
            if (target != sender) {
                sender.sendMessage(Msg.ok("<gray>Tous les items de <aqua>" + target.getName() + "</aqua> ont été réparés <green>(" + repaired + " items)</green>.</gray>"));
            }
            target.sendMessage(Msg.ok("<gray>Tous tes items ont été <green>réparés</green> <green>(" + repaired + " items)</green>.</gray>"));
            return true;
        }

        if (args.length >= 1) {
            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Msg.err("Joueur introuvable."));
                return true;
            }
            ItemStack item = target.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                sender.sendMessage(Msg.err("<aqua>" + target.getName() + "</aqua> n'a rien en main."));
                return true;
            }
            if (!repairItem(item)) {
                sender.sendMessage(Msg.err("Cet item n'a pas de durabilité."));
                return true;
            }
            sender.sendMessage(Msg.ok("<gray>Item en main de <aqua>" + target.getName() + "</aqua> <green>réparé</green>.</gray>"));
            target.sendMessage(Msg.ok("<gray>Ton item en main a été <green>réparé</green> par un admin.</gray>"));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement. Utilisation : /repair [joueur|all] [joueur]");
            return true;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            p.sendMessage(Msg.err("Tu n'as rien en main."));
            return true;
        }
        if (!repairItem(item)) {
            p.sendMessage(Msg.err("Cet item n'a pas de durabilité."));
            return true;
        }
        p.sendMessage(Msg.ok("<gray>Item en main <green>réparé</green>.</gray>"));
        return true;
    }

    private boolean repairItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        if (!damageable.hasDamage() || damageable.getDamage() == 0) return false;
        damageable.setDamage(0);
        item.setItemMeta(meta);
        return true;
    }

    private int repairAll(Player target) {
        int count = 0;
        for (ItemStack item : target.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                if (repairItem(item)) count++;
            }
        }
        for (ItemStack item : target.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                if (repairItem(item)) count++;
            }
        }
        ItemStack offhand = target.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            if (repairItem(offhand)) count++;
        }
        return count;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        var out = new ArrayList<String>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            if ("all".startsWith(pref)) out.add("all");
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
            String pref = args[1].toLowerCase();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
        }
        return out;
    }
}
