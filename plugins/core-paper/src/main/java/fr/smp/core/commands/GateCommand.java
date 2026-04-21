package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.listeners.GateListener;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.GateManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GateCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "wand", "create", "delete", "list", "tp", "radius", "open", "close");

    private final SMPCore plugin;

    public GateCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> handleWand(sender);
            case "create" -> handleCreate(sender, args);
            case "delete", "del", "remove" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "tp" -> handleTp(sender, args);
            case "radius" -> handleRadius(sender, args);
            case "open" -> handleForce(sender, args, true);
            case "close" -> handleForce(sender, args, false);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.info("<aqua>/gate</aqua> <gray>— portes animées</gray>"));
        s.sendMessage(Msg.mm("<gray> • <white>/gate wand</white> — pelle de sélection"));
        s.sendMessage(Msg.mm("<gray> • <white>/gate create <nom> [rayon]</white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/gate delete <nom></white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/gate list</white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/gate tp <nom></white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/gate radius <nom> <r></white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/gate open|close <nom></white> — force"));
    }

    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return;
        }
        p.getInventory().addItem(GateListener.createWand());
        p.sendMessage(Msg.ok("Pelle <aqua>Gate Setup</aqua> reçue. <gray>clic-gauche = pos1, clic-droit = pos2</gray>"));
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Msg.err("/gate create <nom> [rayon]"));
            return;
        }
        String name = args[1];
        if (!name.matches("[a-zA-Z0-9_\\-]{1,32}")) {
            p.sendMessage(Msg.err("Nom invalide (1-32 caractères, a-z 0-9 _ -)."));
            return;
        }
        if (plugin.gates().exists(name)) {
            p.sendMessage(Msg.err("Une gate avec ce nom existe déjà."));
            return;
        }
        double radius = 5.0;
        if (args.length >= 3) {
            try { radius = Math.max(1.0, Math.min(64.0, Double.parseDouble(args[2]))); }
            catch (NumberFormatException e) {
                p.sendMessage(Msg.err("Rayon invalide."));
                return;
            }
        }
        GateManager.Selection sel = plugin.gates().getSelection(p);
        if (sel == null) {
            p.sendMessage(Msg.err("Sélection incomplète. <gray>Utilise la pelle Gate Setup sur pos1 et pos2.</gray>"));
            return;
        }
        GateManager.Gate g = plugin.gates().create(name, sel, radius);
        if (g == null) {
            p.sendMessage(Msg.err("Création échouée (zone vide, monde manquant ou trop gros — max 20k blocs)."));
            return;
        }
        plugin.gates().clearSelection(p);
        p.sendMessage(Msg.ok("Gate <aqua>" + g.name + "</aqua> créée. <gray>" +
                g.blocks.size() + " blocs, rayon " + radius + ".</gray>"));
        plugin.logs().log(LogCategory.ADMIN, p,
                "gate create " + g.name + " @" + g.world + " " + g.blocks.size() + "b r=" + radius);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("/gate delete <nom>"));
            return;
        }
        boolean ok = plugin.gates().delete(args[1]);
        sender.sendMessage(ok ? Msg.ok("<red>Gate supprimée.</red>") : Msg.err("Introuvable."));
        if (ok) plugin.logs().log(LogCategory.ADMIN,
                sender instanceof Player pl ? pl : null, "gate delete " + args[1]);
    }

    private void handleList(CommandSender sender) {
        var all = plugin.gates().all();
        if (all.isEmpty()) {
            sender.sendMessage(Msg.info("<gray>Aucune gate.</gray>"));
            return;
        }
        sender.sendMessage(Msg.info("<aqua>Gates (" + all.size() + ")</aqua>"));
        for (GateManager.Gate g : all) {
            sender.sendMessage(Msg.mm("<gray> • <white>" + g.name + "</white> <dark_gray>(" +
                    g.world + " " + g.x1 + "," + g.y1 + "," + g.z1 + " → " +
                    g.x2 + "," + g.y2 + "," + g.z2 + ", r=" + g.radius +
                    ", " + g.blocks.size() + "b, " + g.getState() + ")</dark_gray>"));
        }
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Msg.err("/gate tp <nom>"));
            return;
        }
        GateManager.Gate g = plugin.gates().get(args[1]);
        if (g == null) {
            p.sendMessage(Msg.err("Gate introuvable."));
            return;
        }
        World w = Bukkit.getWorld(g.world);
        if (w == null) {
            p.sendMessage(Msg.err("Monde de la gate non chargé ici."));
            return;
        }
        Location c = g.center();
        if (c != null) p.teleport(c);
    }

    private void handleRadius(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/gate radius <nom> <r>"));
            return;
        }
        GateManager.Gate g = plugin.gates().get(args[1]);
        if (g == null) {
            sender.sendMessage(Msg.err("Gate introuvable."));
            return;
        }
        double r;
        try { r = Math.max(1.0, Math.min(64.0, Double.parseDouble(args[2]))); }
        catch (NumberFormatException e) {
            sender.sendMessage(Msg.err("Rayon invalide."));
            return;
        }
        plugin.gates().setRadius(g, r);
        sender.sendMessage(Msg.ok("Rayon de <aqua>" + g.name + "</aqua> → <white>" + r + "</white>."));
    }

    private void handleForce(CommandSender sender, String[] args, boolean open) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("/gate " + (open ? "open" : "close") + " <nom>"));
            return;
        }
        GateManager.Gate g = plugin.gates().get(args[1]);
        if (g == null) {
            sender.sendMessage(Msg.err("Gate introuvable."));
            return;
        }
        plugin.gates().forceAnimate(g, open);
        sender.sendMessage(Msg.ok("Gate <aqua>" + g.name + "</aqua> → " + (open ? "<green>OPEN</green>" : "<red>CLOSE</red>")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUBS) if (s.startsWith(pref)) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("delete") || sub.equals("del") || sub.equals("remove")
                    || sub.equals("tp") || sub.equals("radius")
                    || sub.equals("open") || sub.equals("close")) {
                String pref = args[1].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (GateManager.Gate g : plugin.gates().all()) {
                    if (g.name.startsWith(pref)) out.add(g.name);
                }
                return out;
            }
        }
        return List.of();
    }
}
