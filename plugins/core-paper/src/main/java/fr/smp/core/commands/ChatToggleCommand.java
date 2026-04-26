package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class ChatToggleCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;

    public ChatToggleCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin") && !sender.hasPermission("smp.moderation")) {
            sender.sendMessage(Msg.err("Permission refusée.")); return true;
        }

        if (args.length == 0) {
            boolean locked = plugin.isChatLocked();
            sender.sendMessage(Msg.info("Chat: " + (locked
                    ? "<red>verrouillé</red>"
                    : "<green>actif</green>")));
            return true;
        }

        String sub = args[0].toLowerCase();
        String issuer = sender instanceof Player p ? p.getName() : "Console";

        switch (sub) {
            case "lock", "off", "disable" -> {
                if (plugin.isChatLocked()) { sender.sendMessage(Msg.err("Chat déjà verrouillé.")); return true; }
                plugin.setChatLocked(true);
                Bukkit.broadcast(MM.deserialize(
                        "<red><bold>[Chat]</bold></red> <gray>Le chat a été <red>verrouillé</red> par <white>"
                                + issuer + "</white>.</gray>"));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.getMessageChannel() != null) {
                        plugin.getMessageChannel().sendChatLock(true, issuer);
                    }
                });
            }
            case "unlock", "on", "enable" -> {
                if (!plugin.isChatLocked()) { sender.sendMessage(Msg.err("Chat déjà actif.")); return true; }
                plugin.setChatLocked(false);
                Bukkit.broadcast(MM.deserialize(
                        "<green><bold>[Chat]</bold></green> <gray>Le chat a été <green>déverrouillé</green> par <white>"
                                + issuer + "</white>.</gray>"));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.getMessageChannel() != null) {
                        plugin.getMessageChannel().sendChatLock(false, issuer);
                    }
                });
            }
            default -> sender.sendMessage(Msg.err("/chat <lock|unlock>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("lock", "unlock").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
