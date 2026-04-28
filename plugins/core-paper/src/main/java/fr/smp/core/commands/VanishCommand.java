package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.VanishMenuGUI;
import fr.smp.core.managers.VanishManager;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "menu", "gui", "pickup", "level", "preset",
            "stealth", "investigator", "patrol", "build",
            "fly", "god", "speed", "see", "nv");
    private static final List<String> PRESETS = List.of("stealth", "investigator", "patrol", "build");

    private final SMPCore plugin;

    public VanishCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.vanish")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }
        VanishManager v = plugin.vanish();

        if (args.length == 0) {
            v.toggle(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu", "gui" -> {
                if (!v.isVanished(p)) v.toggle(p);
                new VanishMenuGUI(plugin).open(p);
            }
            case "pickup" -> {
                if (!v.isVanished(p)) {
                    p.sendMessage(Msg.err("Tu dois être en vanish."));
                    return true;
                }
                boolean enabled = v.togglePickup(p);
                p.sendMessage(Msg.ok("<gray>Ramassage : "
                        + (enabled ? "<green>activé" : "<red>désactivé") + "</gray>"));
            }
            case "level" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                if (args.length >= 2) {
                    String lvl = args[1].toLowerCase();
                    try {
                        VanishManager.Level target = VanishManager.Level.valueOf(lvl.toUpperCase());
                        v.setLevel(p, target);
                    } catch (IllegalArgumentException e) {
                        p.sendMessage(Msg.err("Niveau invalide. Utilise: normal | super"));
                    }
                } else {
                    v.cycleLevel(p);
                }
            }
            case "preset" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                if (args.length < 2) {
                    p.sendMessage(Msg.err("Usage: /vanish preset <" + String.join("|", PRESETS) + ">"));
                    return true;
                }
                v.applyPreset(p, args[1].toLowerCase());
            }
            case "stealth", "investigator", "patrol", "build" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                v.applyPreset(p, sub);
            }
            case "fly" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                v.toggleFly(p);
            }
            case "god" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                v.toggleGod(p);
            }
            case "speed" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                v.cycleSpeed(p);
            }
            case "see" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                v.toggleSeeOthers(p);
            }
            case "nv" -> {
                if (!v.isVanished(p)) v.enable(p, VanishManager.Level.NORMAL);
                v.toggleNightVision(p);
            }
            default -> {
                p.sendMessage(Msg.err("Sous-commande inconnue. Tape <white>/vanish menu</white>."));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : SUBS) if (pref.isEmpty() || s.startsWith(pref)) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String pref = args[1].toLowerCase();
            if (sub.equals("preset")) {
                List<String> out = new ArrayList<>();
                for (String s : PRESETS) if (pref.isEmpty() || s.startsWith(pref)) out.add(s);
                return out;
            }
            if (sub.equals("level")) {
                List<String> out = new ArrayList<>();
                for (String s : List.of("normal", "super")) if (pref.isEmpty() || s.startsWith(pref)) out.add(s);
                return out;
            }
        }
        return List.of();
    }
}
