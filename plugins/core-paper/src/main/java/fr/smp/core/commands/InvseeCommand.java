package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /invsee opens the target player's live inventory. Only online players
 * for now — offline invsee would require wiring into the SyncManager's
 * serialized inventory snapshot, which is out of scope here.
 */
public class InvseeCommand implements CommandExecutor {

    private final SMPCore plugin;

    public InvseeCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (args.length == 0) { p.sendMessage(Msg.err("/invsee <joueur>")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage(Msg.err("Joueur hors-ligne.")); return true; }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Msg.err("Utilise E pour voir ton inventaire.")); return true;
        }
        p.openInventory(target.getInventory());
        return true;
    }
}
