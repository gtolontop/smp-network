package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ScoreboardToggleCommand implements CommandExecutor {

    private final SMPCore plugin;

    public ScoreboardToggleCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        PlayerData d = plugin.players().get(p);
        if (d == null) return true;
        d.setScoreboardEnabled(!d.scoreboardEnabled());
        if (d.scoreboardEnabled()) {
            plugin.scoreboard().apply(p);
            p.sendMessage(Msg.ok("<green>Scoreboard activé.</green>"));
        } else {
            plugin.scoreboard().remove(p);
            p.sendMessage(Msg.ok("<red>Scoreboard désactivé.</red>"));
        }
        return true;
    }
}
