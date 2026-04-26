package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.BanlistGUI;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BanlistCommand implements CommandExecutor {

    private final SMPCore plugin;

    public BanlistCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.moderation")) {
            sender.sendMessage(Msg.err("Permission refus\u00e9e."));
            return true;
        }

        if (sender instanceof Player p) {
            new BanlistGUI(plugin).open(p, 0);
        } else {
            var bans = plugin.moderation().listBans();
            if (bans.isEmpty()) {
                sender.sendMessage("§aAucun ban actif.");
            } else {
                sender.sendMessage("§c§l" + bans.size() + " ban(s) actif(s):");
                for (var b : bans) {
                    String dur = b.permanent() ? "§4permanent" : "§eexpire " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(b.expiresAt() * 1000L));
                    sender.sendMessage("§7- §f" + b.name() + " §7| §f" + (b.reason() != null ? b.reason() : "-") + " §7| " + dur + " §7| par §b" + b.issuer());
                }
            }
        }
        return true;
    }
}
