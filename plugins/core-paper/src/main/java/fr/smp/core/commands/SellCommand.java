package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.SellGUI;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SellCommand implements CommandExecutor {

    private final SMPCore plugin;

    public SellCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String mode = args.length > 0 ? args[0].toLowerCase() : "gui";
        double total = 0;
        int items = 0;

        switch (mode) {
            case "gui" -> {
                new SellGUI(plugin).open(p);
                return true;
            }
            case "hand" -> {
                ItemStack it = p.getInventory().getItemInMainHand();
                if (it == null || it.getType().isAir()) { p.sendMessage(Msg.err("Rien en main.")); return true; }
                double v = plugin.worth().worth(it);
                if (v <= 0) { p.sendMessage(Msg.err("Cet item n'a pas de valeur.")); return true; }
                total += v;
                items += it.getAmount();
                p.getInventory().setItemInMainHand(null);
            }
            case "all" -> {
                ItemStack[] cts = p.getInventory().getStorageContents();
                for (int i = 0; i < cts.length; i++) {
                    ItemStack s = cts[i];
                    if (s == null || s.getType().isAir()) continue;
                    double v = plugin.worth().worth(s);
                    if (v <= 0) continue;
                    total += v;
                    items += s.getAmount();
                    cts[i] = null;
                }
                p.getInventory().setStorageContents(cts);
            }
            default -> {
                p.sendMessage(Msg.err("/sell [gui|hand|all]"));
                return true;
            }
        }

        if (total <= 0) { p.sendMessage(Msg.err("Rien à vendre.")); return true; }
        plugin.economy().deposit(p.getUniqueId(), total, "sell." + mode);
        p.sendMessage(Msg.ok("<green>Vendu <yellow>×" + items + "</yellow> pour <yellow>$" +
                Msg.money(total) + "</yellow>.</green>"));
        plugin.logs().log(LogCategory.SELL, p, mode + " items=" + items + " $" + total);
        return true;
    }
}
