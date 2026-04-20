package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.gui.FindGUI;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /find — shows teammates only. Admin (smp.admin) can look up anyone.
 */
public class FindCommand implements CommandExecutor {

    private final SMPCore plugin;

    public FindCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (args.length == 0) {
            new FindGUI(plugin).open(p);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage(Msg.err("Joueur hors-ligne.")); return true; }

        boolean admin = p.hasPermission("smp.admin");
        if (!admin) {
            PlayerData pd = plugin.players().get(p);
            PlayerData td = plugin.players().get(target);
            if (pd == null || pd.teamId() == null
                    || td == null || td.teamId() == null
                    || !pd.teamId().equals(td.teamId())) {
                p.sendMessage(Msg.err("<red>Tu ne peux localiser que tes équipiers.</red>"));
                return true;
            }
        }

        Location l = target.getLocation();
        PlayerData d = plugin.players().get(target);
        p.sendMessage(Msg.info("<aqua>" + target.getName() + "</aqua> <gray>→</gray> <white>" +
                l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + "</white>"));
        if (d != null) p.sendMessage(Msg.mm(
                "  <green>$" + Msg.money(d.money()) + "</green>  " +
                "<aqua>◆" + d.shards() + "</aqua>  " +
                "<red>⚔" + d.kills() + "</red>  " +
                "<dark_red>☠" + d.deaths() + "</dark_red>"));
        return true;
    }
}
