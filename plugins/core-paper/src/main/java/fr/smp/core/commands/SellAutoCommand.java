package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellAutoCommand implements CommandExecutor {

    private final SMPCore plugin;

    public SellAutoCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        boolean on = plugin.sellAuto().toggle(p);
        if (on) {
            p.sendMessage(Msg.ok("<green>SellAuto <yellow>activé</yellow> — les items vendables ramassés sont vendus automatiquement.</green>"));
        } else {
            p.sendMessage(Msg.info("<gray>SellAuto <red>désactivé</red>.</gray>"));
        }
        return true;
    }
}
