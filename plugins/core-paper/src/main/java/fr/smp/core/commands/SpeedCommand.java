package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SpeedCommand implements CommandExecutor, TabCompleter {

    private static final float DEFAULT_SPEED = 1.0f;

    private final SMPCore plugin;

    public SpeedCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }

        Player target = player;
        String mode = null;
        float value = DEFAULT_SPEED;

        if (args.length == 1) {
            if (isMode(args[0])) {
                mode = args[0].toLowerCase();
            } else {
                value = parseSpeed(args[0]);
                if (value < 0) {
                    player.sendMessage(Msg.err("Vitesse invalide. Utilise un nombre entre 0 et 10."));
                    return true;
                }
            }
        } else if (args.length >= 2) {
            if (isMode(args[0])) {
                mode = args[0].toLowerCase();
                value = parseSpeed(args[1]);
                if (value < 0) {
                    player.sendMessage(Msg.err("Vitesse invalide. Utilise un nombre entre 0 et 10."));
                    return true;
                }
            } else {
                Player found = plugin.getServer().getPlayer(args[0]);
                if (found == null) {
                    player.sendMessage(Msg.err("Joueur introuvable."));
                    return true;
                }
                target = found;
                value = parseSpeed(args[1]);
                if (value < 0) {
                    player.sendMessage(Msg.err("Vitesse invalide. Utilise un nombre entre 0 et 10."));
                    return true;
                }
            }
        } else if (args.length == 3) {
            if (isMode(args[0])) {
                mode = args[0].toLowerCase();
            }
            Player found = plugin.getServer().getPlayer(args[1]);
            if (found == null) {
                player.sendMessage(Msg.err("Joueur introuvable."));
                return true;
            }
            target = found;
            value = parseSpeed(args[args.length - 1]);
            if (value < 0) {
                player.sendMessage(Msg.err("Vitesse invalide. Utilise un nombre entre 0 et 10."));
                return true;
            }
        }

        if (mode == null || mode.equals("walk")) {
            target.setWalkSpeed(toBukkit(value));
        }
        if (mode == null || mode.equals("fly")) {
            target.setFlySpeed(toBukkit(value));
        }

        String display = String.format("%.1f", value);
        if (target != player) {
            player.sendMessage(Msg.ok("<gray>Vitesse de <aqua>" + target.getName() + "</aqua> définie à <yellow>" + display + "</yellow>.</gray>"));
        }
        target.sendMessage(Msg.ok("<gray>Vitesse définie à <yellow>" + display + "</yellow>.</gray>"));
        return true;
    }

    private boolean isMode(String s) {
        return s.equalsIgnoreCase("walk") || s.equalsIgnoreCase("fly");
    }

    private float parseSpeed(String s) {
        try {
            float v = Float.parseFloat(s);
            if (v < 0 || v > 10) return -1;
            return v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private float toBukkit(float speed) {
        return Math.max(0f, Math.min(1f, speed / 10f));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        var out = new ArrayList<String>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            for (String s : List.of("walk", "fly", "1", "2", "3", "5", "10")) {
                if (s.startsWith(pref)) out.add(s);
            }
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
        } else if (args.length == 2) {
            String pref = args[1].toLowerCase();
            for (String s : List.of("1", "2", "3", "5", "10")) {
                if (s.startsWith(pref)) out.add(s);
            }
        }
        return out;
    }
}
