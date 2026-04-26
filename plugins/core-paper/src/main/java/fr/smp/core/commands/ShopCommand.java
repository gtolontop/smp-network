package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.ShopGUI;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {

    private final SMPCore plugin;

    public ShopCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && sender.hasPermission("smp.admin")) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "on", "enable" -> {
                    plugin.shop().setEnabled(true);
                    sender.sendMessage(Msg.ok("<green>Shop activé.</green>"));
                    return true;
                }
                case "off", "disable" -> {
                    plugin.shop().setEnabled(false);
                    sender.sendMessage(Msg.ok("<green>Shop désactivé pour les joueurs.</green>"));
                    return true;
                }
                case "toggle" -> {
                    boolean now = !plugin.shop().isEnabled();
                    plugin.shop().setEnabled(now);
                    sender.sendMessage(Msg.ok("<green>Shop "
                            + (now ? "activé" : "désactivé") + ".</green>"));
                    return true;
                }
                case "reload" -> {
                    plugin.shop().load();
                    sender.sendMessage(Msg.ok("<green>Shop rechargé.</green>"));
                    return true;
                }
            }
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.err("Commande réservée aux joueurs."));
            return true;
        }

        if (!plugin.shop().isEnabled() && !p.hasPermission("smp.admin")) {
            p.sendMessage(Msg.err("Le shop est désactivé."));
            return true;
        }

        new ShopGUI(plugin).open(p);
        return true;
    }
}
