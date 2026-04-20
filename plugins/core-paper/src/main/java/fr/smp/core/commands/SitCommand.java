package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SitCommand implements CommandExecutor {

    private final SMPCore plugin;

    public SitCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (plugin.sit().isSeated(p)) {
            plugin.sit().stand(p);
            p.sendMessage(Msg.ok("<gray>Tu te relèves.</gray>"));
            return true;
        }
        Block target = p.getTargetBlockExact(4);
        if (target == null || !target.getType().isSolid()) {
            p.sendMessage(Msg.err("Regarde un bloc solide (≤ 4 blocs)."));
            return true;
        }
        if (!plugin.sit().sit(p, target.getLocation())) {
            p.sendMessage(Msg.err("Impossible de s'asseoir ici."));
            return true;
        }
        p.sendMessage(Msg.ok("<gray>Tu t'assieds. Shift pour te lever.</gray>"));
        return true;
    }
}
