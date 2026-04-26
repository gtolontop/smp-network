package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FullbrightCommand implements CommandExecutor {

    private final SMPCore plugin;

    public FullbrightCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }
        if (!player.hasPermission("smp.fullbright")) {
            player.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        PlayerData data = plugin.players().get(player);
        if (data == null) {
            player.sendMessage(Msg.err("Profil introuvable."));
            return true;
        }

        boolean enabled = plugin.fullbright().toggle(player);
        if (enabled) {
            player.sendMessage(Msg.ok("<yellow>Fullbright activé.</yellow>"));
        } else {
            player.sendMessage(Msg.ok("<gray>Fullbright <red>désactivé</red>.</gray>"));
        }
        return true;
    }
}
