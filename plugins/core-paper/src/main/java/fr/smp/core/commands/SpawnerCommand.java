package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.SpawnerManager;
import fr.smp.core.managers.SpawnerType;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpawnerCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public SpawnerCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        SpawnerManager mgr = plugin.spawners();
        if (mgr == null) {
            sender.sendMessage(Msg.err("Les spawners ne sont pas actifs sur ce serveur."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Msg.info("<gray>Usage: /spawner give <joueur> <type> [amount] [stack]</gray>"));
            sender.sendMessage(Msg.info("<gray>       /spawner types</gray>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "types" -> {
                StringBuilder b = new StringBuilder("<gray>Types disponibles: ");
                boolean first = true;
                for (SpawnerType t : SpawnerType.all()) {
                    if (!first) b.append("<dark_gray>,</dark_gray> ");
                    b.append(t.colorTag()).append(t.name().toLowerCase()).append("<reset>");
                    first = false;
                }
                b.append("</gray>");
                sender.sendMessage(Msg.info(b.toString()));
                return true;
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(Msg.err("Usage: /spawner give <joueur> <type> [amount] [stack]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Msg.err("Joueur introuvable."));
                    return true;
                }
                SpawnerType type = SpawnerType.fromId(args[2]);
                if (type == null) {
                    sender.sendMessage(Msg.err("Type inconnu. /spawner types pour la liste."));
                    return true;
                }
                int amount = 1;
                int stack = 1;
                if (args.length >= 4) {
                    try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException e) {
                        sender.sendMessage(Msg.err("Amount invalide."));
                        return true;
                    }
                }
                if (args.length >= 5) {
                    try { stack = Math.max(1, Integer.parseInt(args[4])); } catch (NumberFormatException e) {
                        sender.sendMessage(Msg.err("Stack invalide."));
                        return true;
                    }
                }
                for (int i = 0; i < amount; i++) {
                    ItemStack it = mgr.makeSpawnerItem(type, stack);
                    Map<Integer, ItemStack> overflow = target.getInventory().addItem(it);
                    overflow.values().forEach(o -> target.getWorld().dropItemNaturally(target.getLocation(), o));
                }
                sender.sendMessage(Msg.ok("<green>Donné <yellow>×" + amount + "</yellow> spawner "
                        + type.colorTag() + type.display() + "<green>"
                        + (stack > 1 ? " (stack ×" + stack + ")" : "") + " à " + target.getName() + ".</green>"));
                if (!sender.equals(target)) {
                    target.sendMessage(Msg.info("<green>Tu as reçu un spawner " + type.colorTag()
                            + type.display() + "<green>.</green>"));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Msg.err("Sous-commande inconnue."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("give", "types")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String pref = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (pref.isEmpty() || p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String pref = args[2].toLowerCase();
            for (String s : SpawnerType.ids()) {
                String low = s.toLowerCase();
                if (pref.isEmpty() || low.startsWith(pref)) out.add(low);
            }
            return out;
        }
        return out;
    }
}
