package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HereCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public HereCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        Location l = p.getLocation();
        String line = "<aqua>" + p.getName() + "</aqua> <gray>est à</gray> <white>" +
                l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() +
                "</white>";
        String rendered = mm.serialize(Msg.info(line));
        for (Player other : Bukkit.getOnlinePlayers()) other.sendMessage(Msg.info(line));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getMessageChannel() != null) {
                plugin.getMessageChannel().sendHere(p.getName(), rendered);
            }
        });
        return true;
    }
}
