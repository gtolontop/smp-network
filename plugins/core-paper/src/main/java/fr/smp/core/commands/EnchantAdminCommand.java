package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.enchants.CustomEnchant;
import fr.smp.core.enchants.EnchantEngine;
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

/** /ce — admin tooling for custom enchants: give, book, apply, list. */
public class EnchantAdminCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public EnchantAdminCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) { usage(s); return true; }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> listAll(s);
            case "book" -> giveBook(s, args);
            case "apply" -> applyToHand(s, args);
            case "give" -> giveBook(s, args); // alias
            case "clear" -> clearHand(s);
            default -> usage(s);
        }
        return true;
    }

    private void usage(CommandSender s) {
        s.sendMessage(Msg.info("<gray>Usage :</gray>"));
        s.sendMessage(Msg.mm("  <aqua>/ce list</aqua> <dark_gray>—</dark_gray> <gray>liste des enchants</gray>"));
        s.sendMessage(Msg.mm("  <aqua>/ce book <id> [level] [player]</aqua> <dark_gray>—</dark_gray> <gray>donner un livre</gray>"));
        s.sendMessage(Msg.mm("  <aqua>/ce apply <id> <level></aqua> <dark_gray>—</dark_gray> <gray>applique sur l'objet en main</gray>"));
        s.sendMessage(Msg.mm("  <aqua>/ce clear</aqua> <dark_gray>—</dark_gray> <gray>retire les enchants customs de l'objet en main</gray>"));
    }

    private void listAll(CommandSender s) {
        s.sendMessage(Msg.info("<gradient:#67e8f9:#a78bfa><bold>Enchants customs</bold></gradient>"));
        for (CustomEnchant ce : CustomEnchant.values()) {
            String tag = ce.isOvercap() ? "<gold>[OVERCAP]</gold>" : "<light_purple>[CUSTOM]</light_purple>";
            s.sendMessage(Msg.mm(" " + tag + " <white>" + ce.displayName() + "</white> " +
                    "<dark_gray>(" + ce.id() + ", max " + ce.maxLevel() + ")</dark_gray> " +
                    "<gray>→ " + ce.target().name().toLowerCase() + "</gray>"));
        }
    }

    private void giveBook(CommandSender s, String[] args) {
        if (!s.hasPermission("smp.admin")) { s.sendMessage(Msg.err("Permission refusée.")); return; }
        if (args.length < 2) { s.sendMessage(Msg.err("Usage: /ce book <id> [level] [player]")); return; }

        var opt = CustomEnchant.byId(args[1]);
        if (opt.isEmpty()) { s.sendMessage(Msg.err("Enchant inconnu: " + args[1])); return; }
        CustomEnchant ce = opt.get();

        int level = ce.maxLevel();
        if (args.length >= 3) {
            try { level = Integer.parseInt(args[2]); }
            catch (NumberFormatException nfe) { s.sendMessage(Msg.err("Niveau invalide.")); return; }
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) { s.sendMessage(Msg.err("Joueur introuvable: " + args[3])); return; }
        } else if (s instanceof Player p) {
            target = p;
        } else {
            s.sendMessage(Msg.err("Spécifie un joueur.")); return;
        }

        ItemStack book = EnchantEngine.book(ce, level);
        var leftover = target.getInventory().addItem(book);
        for (ItemStack over : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), over);
        }
        plugin.getSyncManager().markDirty(target);
        s.sendMessage(Msg.ok("Livre donné à <aqua>" + target.getName() + "</aqua> : " +
                ce.displayName() + " " + CustomEnchant.roman(level)));
    }

    private void applyToHand(CommandSender s, String[] args) {
        if (!s.hasPermission("smp.admin")) { s.sendMessage(Msg.err("Permission refusée.")); return; }
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        if (args.length < 3) { s.sendMessage(Msg.err("Usage: /ce apply <id> <level>")); return; }

        var opt = CustomEnchant.byId(args[1]);
        if (opt.isEmpty()) { s.sendMessage(Msg.err("Enchant inconnu.")); return; }
        int level;
        try { level = Integer.parseInt(args[2]); }
        catch (NumberFormatException nfe) { s.sendMessage(Msg.err("Niveau invalide.")); return; }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            s.sendMessage(Msg.err("Tiens un objet en main.")); return;
        }
        CustomEnchant ce = opt.get();
        if (!ce.target().matches(hand.getType())) {
            s.sendMessage(Msg.err("L'objet en main n'est pas compatible avec " + ce.displayName() + "."));
            return;
        }
        EnchantEngine.apply(hand, ce, level);
        plugin.getSyncManager().markDirty(p);
        p.sendMessage(Msg.ok("Appliqué " + ce.displayName() + " " + CustomEnchant.roman(level) + "."));
    }

    private void clearHand(CommandSender s) {
        if (!s.hasPermission("smp.admin")) { s.sendMessage(Msg.err("Permission refusée.")); return; }
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { s.sendMessage(Msg.err("Tiens un objet en main.")); return; }
        EnchantEngine.removeAll(hand);
        plugin.getSyncManager().markDirty(p);
        p.sendMessage(Msg.ok("Enchants customs retirés."));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("list", "book", "apply", "clear"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("book") || args[0].equalsIgnoreCase("apply"))) {
            List<String> ids = new ArrayList<>();
            for (CustomEnchant ce : CustomEnchant.values()) ids.add(ce.id());
            return filter(ids, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("book") || args[0].equalsIgnoreCase("apply"))) {
            var opt = CustomEnchant.byId(args[1]);
            if (opt.isPresent()) {
                List<String> lvls = new ArrayList<>();
                for (int i = 1; i <= opt.get().maxLevel(); i++) lvls.add(String.valueOf(i));
                return filter(lvls, args[2]);
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("book")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> in, String pref) {
        String lp = pref.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : in) if (lp.isEmpty() || s.toLowerCase().startsWith(lp)) out.add(s);
        return out;
    }
}
