package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NickCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_LENGTH = 16;

    private final SMPCore plugin;

    public NickCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.err("Seuls les joueurs peuvent utiliser cette commande."));
            return true;
        }

        if (args.length == 0) {
            showCurrent(sender, player);
            return true;
        }

        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("reset")) {
            handleOff(sender, player);
            return true;
        }

        if (sender.hasPermission("smp.admin") && args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Msg.err("Joueur introuvable."));
                return true;
            }
            String nick = buildNick(args, 1);
            setNick(sender, target, nick);
            return true;
        }

        String nick = buildNick(args, 0);
        setNick(sender, player, nick);
        return true;
    }

    private void showCurrent(CommandSender sender, Player p) {
        PlayerData d = plugin.players().get(p);
        if (d == null || d.nickname() == null || d.nickname().isEmpty()) {
            sender.sendMessage(Msg.info("Tu n'as pas de surnom. Utilise <white>/nick <surnom></white> pour en définir un."));
            return;
        }
        sender.sendMessage(Msg.info("Ton surnom actuel : <white>" + d.nickname() + "</white>"));
    }

    private void handleOff(CommandSender sender, Player p) {
        PlayerData d = plugin.players().get(p);
        if (d == null) return;
        d.setNickname(null);
        plugin.players().save(d);
        refreshDisplay(p);
        sender.sendMessage(Msg.ok("Surnom supprimé."));
    }

    private void setNick(CommandSender sender, Player target, String nick) {
        String plain = stripMiniMessage(nick);
        if (plain.isEmpty()) {
            sender.sendMessage(Msg.err("Le surnom ne peut pas être vide."));
            return;
        }
        if (plain.length() > MAX_LENGTH) {
            sender.sendMessage(Msg.err("Le surnom ne doit pas dépasser " + MAX_LENGTH + " caractères."));
            return;
        }
        if (!plain.matches("[A-Za-z0-9_]+")) {
            sender.sendMessage(Msg.err("Le surnom ne peut contenir que des lettres, chiffres et underscores."));
            return;
        }

        PlayerData d = plugin.players().get(target);
        if (d == null) {
            sender.sendMessage(Msg.err("Données joueur introuvables."));
            return;
        }

        boolean canColor = sender.hasPermission("smp.nick.color");
        String finalNick = canColor ? nick : plain;

        d.setNickname(finalNick);
        plugin.players().save(d);
        refreshDisplay(target);

        if (sender.equals(target)) {
            sender.sendMessage(Msg.ok("Surnom défini sur <white>" + finalNick + "</white>."));
        } else {
            sender.sendMessage(Msg.ok("Surnom de <white>" + target.getName() + "</white> défini sur <white>" + finalNick + "</white>."));
            target.sendMessage(Msg.ok("Ton surnom a été changé en <white>" + finalNick + "</white>."));
        }
    }

    private void refreshDisplay(Player p) {
        if (plugin.tabList() != null) plugin.tabList().update(p);
        if (plugin.nametags() != null) plugin.nametags().refresh(p);
    }

    private String buildNick(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private String stripMiniMessage(String input) {
        return input.replaceAll("<[^>]+>", "").replaceAll("&[0-9a-fk-orA-FK-OR#]", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("off");
            if (sender.hasPermission("smp.admin")) {
                String lower = args[0].toLowerCase(Locale.ROOT);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                        suggestions.add(p.getName());
                    }
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
