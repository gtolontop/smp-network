package fr.smp.core.holograms;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /holo create <nom>
 * /holo remove <nom>
 * /holo list
 * /holo addline <nom> <texte...>     — MiniMessage supporté
 * /holo insert <nom> <index> <texte...>
 * /holo setline <nom> <index> <texte...>
 * /holo delline <nom> <index>
 * /holo lines <nom>
 * /holo tp <nom>
 * /holo here <nom>
 */
public class HologramCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public HologramCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("smp.admin")) { s.sendMessage(Msg.err("Permission requise.")); return true; }
        if (a.length == 0) { help(s); return true; }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "create" -> cmdCreate(s, a);
            case "remove", "delete" -> cmdRemove(s, a);
            case "list" -> cmdList(s);
            case "addline" -> cmdAddLine(s, a);
            case "insert" -> cmdInsert(s, a);
            case "setline" -> cmdSetLine(s, a);
            case "delline" -> cmdDelLine(s, a);
            case "lines" -> cmdLines(s, a);
            case "tp" -> cmdTp(s, a);
            case "here", "move" -> cmdHere(s, a);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.info("<gold>Holograms</gold> — /holo create|remove|list|addline|insert|setline|delline|lines|tp|here"));
    }

    private void cmdCreate(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /holo create <nom>")); return; }
        Hologram h = plugin.holograms().create(a[1], p.getLocation().add(0, 1.5, 0));
        if (h == null) { s.sendMessage(Msg.err("Nom déjà utilisé.")); return; }
        s.sendMessage(Msg.ok("Hologramme <white>" + a[1] + "</white> créé."));
    }

    private void cmdRemove(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /holo remove <nom>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        plugin.holograms().remove(h);
        s.sendMessage(Msg.ok("Supprimé."));
    }

    private void cmdList(CommandSender s) {
        if (plugin.holograms().all().isEmpty()) {
            s.sendMessage(Msg.info("Aucun hologramme."));
            return;
        }
        s.sendMessage(Msg.info("<gold>Holograms</gold> (" + plugin.holograms().all().size() + ")"));
        for (Hologram h : plugin.holograms().all().values()) {
            s.sendMessage(Msg.mm("<gray>- <white>" + h.name() + "</white> <dark_gray>"
                    + h.location().getWorld().getName() + " "
                    + (int) h.location().getX() + "/"
                    + (int) h.location().getY() + "/"
                    + (int) h.location().getZ()
                    + "</dark_gray> <gray>(" + h.lines().size() + " lignes)</gray>"));
        }
    }

    private void cmdAddLine(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(Msg.err("Usage: /holo addline <nom> <texte>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        String text = join(a, 2);
        plugin.holograms().addLine(h, text);
        s.sendMessage(Msg.ok("Ligne ajoutée."));
    }

    private void cmdInsert(CommandSender s, String[] a) {
        if (a.length < 4) { s.sendMessage(Msg.err("Usage: /holo insert <nom> <index> <texte>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        int idx = parseInt(a[2], -1);
        if (idx < 0) { s.sendMessage(Msg.err("Index invalide.")); return; }
        plugin.holograms().insertLine(h, idx, join(a, 3));
        s.sendMessage(Msg.ok("Ligne insérée."));
    }

    private void cmdSetLine(CommandSender s, String[] a) {
        if (a.length < 4) { s.sendMessage(Msg.err("Usage: /holo setline <nom> <index> <texte>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        int idx = parseInt(a[2], -1);
        if (!plugin.holograms().editLine(h, idx, join(a, 3))) {
            s.sendMessage(Msg.err("Index invalide.")); return;
        }
        s.sendMessage(Msg.ok("Ligne modifiée."));
    }

    private void cmdDelLine(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(Msg.err("Usage: /holo delline <nom> <index>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        int idx = parseInt(a[2], -1);
        if (!plugin.holograms().removeLine(h, idx)) {
            s.sendMessage(Msg.err("Index invalide.")); return;
        }
        s.sendMessage(Msg.ok("Ligne supprimée."));
    }

    private void cmdLines(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /holo lines <nom>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        s.sendMessage(Msg.info("Lignes de <white>" + h.name() + "</white> :"));
        List<String> l = h.lines();
        for (int i = 0; i < l.size(); i++) {
            s.sendMessage(Msg.mm("<dark_gray>[" + i + "]</dark_gray> <gray>" + l.get(i) + "</gray>"));
        }
    }

    private void cmdTp(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /holo tp <nom>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        p.teleport(h.location());
        s.sendMessage(Msg.ok("Téléporté."));
    }

    private void cmdHere(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /holo here <nom>")); return; }
        Hologram h = plugin.holograms().byName(a[1]);
        if (h == null) { s.sendMessage(Msg.err("Introuvable.")); return; }
        plugin.holograms().move(h, p.getLocation().add(0, 1.5, 0));
        s.sendMessage(Msg.ok("Déplacé."));
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private static String join(String[] a, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("smp.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String sub : List.of("create", "remove", "list", "addline", "insert",
                    "setline", "delline", "lines", "tp", "here")) {
                if (sub.startsWith(a[0].toLowerCase())) out.add(sub);
            }
            return out;
        }
        if (a.length == 2) {
            String sub = a[0].toLowerCase();
            if (sub.equals("remove") || sub.equals("addline") || sub.equals("insert")
                    || sub.equals("setline") || sub.equals("delline") || sub.equals("lines")
                    || sub.equals("tp") || sub.equals("here")) {
                for (Hologram h : plugin.holograms().all().values()) {
                    if (h.name().toLowerCase().startsWith(a[1].toLowerCase())) out.add(h.name());
                }
            }
        }
        return out;
    }
}
