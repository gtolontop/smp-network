package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class DiscordConfigCommand implements CommandExecutor {

    private final SMPCore plugin;

    public DiscordConfigCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        if (args.length < 1) {
            String current = plugin.getConfig().getString("discord.invite-link", "");
            if (current == null || current.isBlank()) {
                sender.sendMessage(Msg.info("Aucun lien Discord défini. Utilisation : <gray>/discordconfig <lien></gray>"));
            } else {
                sender.sendMessage(Msg.info("Lien Discord actuel : <aqua>" + current + "</aqua>"));
                sender.sendMessage(Msg.info("Pour changer : <gray>/discordconfig <lien></gray> — pour effacer : <gray>/discordconfig clear</gray>"));
            }
            return true;
        }

        String raw = String.join(" ", args).trim();

        if (raw.equalsIgnoreCase("clear") || raw.equalsIgnoreCase("reset") || raw.equalsIgnoreCase("none")) {
            plugin.getConfig().set("discord.invite-link", "");
            plugin.saveConfig();
            sender.sendMessage(Msg.ok("Lien Discord effacé."));
            return true;
        }

        if (raw.length() >= 2 && ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }

        if (raw.isEmpty()) {
            sender.sendMessage(Msg.err("Lien vide."));
            return true;
        }

        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            sender.sendMessage(Msg.err("Le lien doit commencer par <gray>http://</gray> ou <gray>https://</gray>."));
            return true;
        }

        plugin.getConfig().set("discord.invite-link", raw);
        plugin.saveConfig();
        sender.sendMessage(Msg.ok("Lien Discord défini : <aqua>" + raw + "</aqua>"));
        return true;
    }
}
