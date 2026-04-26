package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WorthCommand implements CommandExecutor {

    private final SMPCore plugin;

    public WorthCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            p.sendMessage(Msg.err("Rien en main."));
            return true;
        }
        double total = plugin.worth().worth(held);
        if (total <= 0) {
            p.sendMessage(Msg.err("Cet item n'a pas de valeur définie."));
            return true;
        }
        int amount = Math.max(1, held.getAmount());
        double unit = total / amount;
        String name = held.getType().name().replace('_', ' ').toLowerCase();
        p.sendMessage(Msg.info("<yellow>" + name + "</yellow> "
                + "<gray>×" + amount + "</gray>"
                + " <dark_gray>→</dark_gray>"
                + " <green>$" + Msg.money(unit) + "</green> <gray>/ unité</gray>"
                + (amount > 1 ? "  <dark_gray>|</dark_gray>  <green>$" + Msg.money(total) + "</green> <gray>total</gray>" : "")));
        return true;
    }
}
