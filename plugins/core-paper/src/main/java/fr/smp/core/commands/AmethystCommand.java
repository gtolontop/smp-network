package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.AmethystBoostManager;
import fr.smp.core.managers.AmethystBoostManager.Preset;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AmethystCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("info", "preset", "set", "toggle", "presets", "rescan");
    private static final List<String> FIELDS = List.of("pulse", "attempts", "chance");

    private final SMPCore plugin;

    public AmethystCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        AmethystBoostManager mgr = plugin.amethyst();
        if (mgr == null) {
            sender.sendMessage(Msg.err("Le boost amethyst n'est pas actif sur ce serveur."));
            return true;
        }

        if (args.length == 0) {
            usage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info", "status" -> {
                Map<String, String> d = mgr.describe();
                sender.sendMessage(Msg.info("<gradient:#c4b5fd:#f0abfc><bold>Amethyst Booster</bold></gradient>"));
                for (Map.Entry<String, String> e : d.entrySet()) {
                    sender.sendMessage(Msg.mm("  <gray>" + e.getKey() + ":</gray> <white>" + e.getValue() + "</white>"));
                }
                return true;
            }
            case "presets" -> {
                StringBuilder b = new StringBuilder("<gray>Presets: ");
                boolean first = true;
                for (Preset p : Preset.values()) {
                    if (!first) b.append("<dark_gray>,</dark_gray> ");
                    b.append(presetColor(p)).append(p.id).append("<reset>");
                    first = false;
                }
                b.append("</gray>");
                sender.sendMessage(Msg.info(b.toString()));
                sender.sendMessage(Msg.mm("<gray>  off</gray> <dark_gray>—</dark_gray> <white>désactivé</white>"));
                sender.sendMessage(Msg.mm("<gray>  slow</gray> <dark_gray>—</dark_gray> <white>proche du vanilla</white>"));
                sender.sendMessage(Msg.mm("<gray>  medium</gray> <dark_gray>—</dark_gray> <white>quelques sec / étape</white>"));
                sender.sendMessage(Msg.mm("<gray>  fast</gray> <dark_gray>—</dark_gray> <white>2 essais / 0.5s</white>"));
                sender.sendMessage(Msg.mm("<gray>  hyper</gray> <dark_gray>—</dark_gray> <white>4 essais / 0.25s</white>"));
                sender.sendMessage(Msg.mm("<gray>  insane</gray> <dark_gray>—</dark_gray> <white>6 essais / tick</white>"));
                return true;
            }
            case "preset" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.err("Usage: /amethyst preset <off|slow|medium|fast|hyper|insane>"));
                    return true;
                }
                Preset p = Preset.fromId(args[1]);
                if (p == null) {
                    sender.sendMessage(Msg.err("Preset inconnu. /amethyst presets pour la liste."));
                    return true;
                }
                mgr.applyPreset(p);
                sender.sendMessage(Msg.ok("Preset appliqué: " + presetColor(p) + p.id + "<reset>"
                        + " <gray>(pulse=" + p.pulseTicks + "t, attempts=" + p.attemptsPerPulse
                        + ", chance=" + String.format("%.2f", p.chance) + ", enabled=" + p.enabled + ")</gray>"));
                return true;
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(Msg.err("Usage: /amethyst set <pulse|attempts|chance> <valeur>"));
                    return true;
                }
                if (!mgr.setField(args[1], args[2])) {
                    sender.sendMessage(Msg.err("Valeur invalide. Champs: pulse(>=1), attempts(>=0), chance(0.0-1.0)."));
                    return true;
                }
                sender.sendMessage(Msg.ok("<gray>" + args[1] + " = " + args[2] + "</gray>"));
                return true;
            }
            case "toggle" -> {
                boolean v = !mgr.isEnabled();
                mgr.setEnabled(v);
                sender.sendMessage(Msg.ok("Booster: " + (v ? "<green>ON</green>" : "<red>OFF</red>")));
                return true;
            }
            case "rescan" -> {
                int before = mgr.trackedCount();
                for (var w : plugin.getServer().getWorlds()) {
                    for (var ch : w.getLoadedChunks()) mgr.scanChunk(ch);
                }
                int after = mgr.trackedCount();
                sender.sendMessage(Msg.ok("Rescan: <yellow>" + before + "</yellow> → <yellow>" + after + "</yellow> blocs trackés."));
                return true;
            }
            default -> {
                usage(sender);
                return true;
            }
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Msg.info("<gray>Usage:</gray>"));
        sender.sendMessage(Msg.mm("<gray>  /amethyst info</gray>"));
        sender.sendMessage(Msg.mm("<gray>  /amethyst presets</gray>"));
        sender.sendMessage(Msg.mm("<gray>  /amethyst preset <off|slow|medium|fast|hyper|insane></gray>"));
        sender.sendMessage(Msg.mm("<gray>  /amethyst set <pulse|attempts|chance> <valeur></gray>"));
        sender.sendMessage(Msg.mm("<gray>  /amethyst toggle</gray>"));
        sender.sendMessage(Msg.mm("<gray>  /amethyst rescan</gray>"));
    }

    private static String presetColor(Preset p) {
        return switch (p) {
            case OFF    -> "<gray>";
            case SLOW   -> "<aqua>";
            case MEDIUM -> "<green>";
            case FAST   -> "<yellow>";
            case HYPER  -> "<gold>";
            case INSANE -> "<light_purple>";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            for (String s : SUBS) if (pref.isEmpty() || s.startsWith(pref)) out.add(s);
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("preset")) {
            String pref = args[1].toLowerCase();
            for (Preset p : Preset.values()) if (pref.isEmpty() || p.id.startsWith(pref)) out.add(p.id);
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            String pref = args[1].toLowerCase();
            for (String f : FIELDS) if (pref.isEmpty() || f.startsWith(pref)) out.add(f);
            return out;
        }
        return out;
    }
}
