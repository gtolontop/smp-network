package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GamemodeCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public GamemodeCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        // Shortcuts: /gmc /gms /gma /gmsp already encode the mode in the label.
        GameMode mode = shortcutMode(label);
        int playerArgIdx = 0;

        if (mode == null) {
            // /gamemode <mode> [player] or /gm <mode> [player]
            if (args.length < 1) {
                sender.sendMessage(Msg.err("Usage: /gamemode <creative|survival|adventure|spectator> [joueur]"));
                return true;
            }
            mode = parseMode(args[0]);
            if (mode == null) {
                sender.sendMessage(Msg.err("Mode inconnu : <white>" + args[0]
                        + "</white>. (creative|survival|adventure|spectator)"));
                return true;
            }
            playerArgIdx = 1;
        }

        Player target;
        if (args.length > playerArgIdx) {
            target = plugin.getServer().getPlayer(args[playerArgIdx]);
            if (target == null) {
                sender.sendMessage(Msg.err("Joueur introuvable : <white>" + args[playerArgIdx] + "</white>"));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Msg.err("Spécifie un joueur."));
            return true;
        }

        target.setGameMode(mode);

        String modeName = switch (mode) {
            case CREATIVE   -> "Créatif";
            case SURVIVAL   -> "Survie";
            case ADVENTURE  -> "Aventure";
            case SPECTATOR  -> "Spectateur";
        };

        if (target == sender) {
            sender.sendMessage(Msg.ok("<gray>Mode de jeu → <aqua>" + modeName + "</aqua>.</gray>"));
        } else {
            sender.sendMessage(Msg.ok("<gray>Mode de jeu de <aqua>" + target.getName()
                    + "</aqua> → <aqua>" + modeName + "</aqua>.</gray>"));
            target.sendMessage(Msg.ok("<gray>Mode de jeu changé en <aqua>" + modeName + "</aqua>.</gray>"));
        }
        return true;
    }

    private GameMode shortcutMode(String label) {
        return switch (label.toLowerCase()) {
            case "gmc"  -> GameMode.CREATIVE;
            case "gms"  -> GameMode.SURVIVAL;
            case "gma"  -> GameMode.ADVENTURE;
            case "gmsp" -> GameMode.SPECTATOR;
            default     -> null;
        };
    }

    private GameMode parseMode(String s) {
        return switch (s.toLowerCase()) {
            case "creative",  "c", "1" -> GameMode.CREATIVE;
            case "survival",  "s", "0" -> GameMode.SURVIVAL;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp","3" -> GameMode.SPECTATOR;
            default -> {
                try { yield GameMode.getByValue(Integer.parseInt(s)); }
                catch (NumberFormatException ignored) { yield null; }
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        boolean isShortcut = shortcutMode(alias) != null;
        // Shortcut: first arg (index 0) is the player name.
        // Full form: first arg is the mode, second (index 1) is the player.
        if (!isShortcut && args.length == 1) {
            String pref = args[0].toLowerCase();
            return List.of("creative", "survival", "adventure", "spectator").stream()
                    .filter(s -> s.startsWith(pref)).toList();
        }
        int playerArgIdx = isShortcut ? 0 : 1;
        if (args.length == playerArgIdx + 1) {
            var out = new ArrayList<String>();
            String pref = args[playerArgIdx].toLowerCase();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
            return out;
        }
        return List.of();
    }
}
