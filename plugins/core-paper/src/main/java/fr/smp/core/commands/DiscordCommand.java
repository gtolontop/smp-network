package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class DiscordCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;

    public DiscordCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String link = plugin.getConfig().getString("discord.invite-link", "");
        if (link == null || link.isBlank()) {
            sender.sendMessage(Msg.err("Aucun lien Discord configuré. Un admin peut le définir avec /discordconfig <lien>."));
            return true;
        }

        String safe = link.replace("\\", "\\\\").replace("'", "\\'");
        Component msg = MM.deserialize(
                Msg.PREFIX + "<gray>Rejoins-nous sur Discord :</gray> "
                        + "<click:open_url:'" + safe + "'><hover:show_text:'<green>Clique pour ouvrir</green>'>"
                        + "<aqua><u>" + link + "</u></aqua></hover></click>");
        sender.sendMessage(msg);
        return true;
    }
}
