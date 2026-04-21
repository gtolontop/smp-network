package fr.smp.core.npc;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /npc create <pseudo> [skin=<joueur>] [wander]
 * /npc remove <pseudo>
 * /npc list
 * /npc skin <pseudo> <joueur>
 * /npc wander <pseudo>
 * /npc tp <pseudo>
 * /npc here <pseudo>         — téléporte le NPC sur ta position
 * /npc rename <pseudo> <nouveau>
 */
public class NpcCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public NpcCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("smp.admin")) {
            s.sendMessage(Msg.err("Permission requise."));
            return true;
        }
        if (a.length == 0) { help(s); return true; }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "create" -> cmdCreate(s, a);
            case "remove", "delete" -> cmdRemove(s, a);
            case "list" -> cmdList(s);
            case "skin" -> cmdSkin(s, a);
            case "wander" -> cmdWander(s, a);
            case "tp" -> cmdTp(s, a);
            case "here", "move" -> cmdHere(s, a);
            case "rename" -> cmdRename(s, a);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.info("<gold>NPC</gold> — /npc create|remove|list|skin|wander|tp|here|rename"));
    }

    private void cmdCreate(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /npc create <pseudo> [skin=<joueur>] [wander]")); return; }
        String name = a[1];
        String skinOwner = null;
        boolean wander = false;
        for (int i = 2; i < a.length; i++) {
            String arg = a[i];
            if (arg.toLowerCase().startsWith("skin=")) skinOwner = arg.substring(5);
            else if (arg.equalsIgnoreCase("wander")) wander = true;
        }
        final boolean fWander = wander;
        final String fSkinOwner = skinOwner != null ? skinOwner : name;
        s.sendMessage(Msg.info("Récupération du skin de <white>" + fSkinOwner + "</white>…"));
        SkinFetcher.fetch(fSkinOwner).thenAccept(skin -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (skin == null) {
                s.sendMessage(Msg.err("Skin introuvable pour <white>" + fSkinOwner + "</white> — NPC créé sans skin."));
            }
            Npc npc = plugin.npcs().create(p.getLocation(), name, skin, fWander);
            if (npc == null) { s.sendMessage(Msg.err("Création impossible.")); return; }
            s.sendMessage(Msg.ok("NPC <white>" + name + "</white> créé (#" + npc.id() + ")"));
        }));
    }

    private void cmdRemove(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /npc remove <pseudo>")); return; }
        Npc npc = plugin.npcs().byName(a[1]);
        if (npc == null) { s.sendMessage(Msg.err("NPC introuvable.")); return; }
        if (plugin.npcs().remove(npc)) s.sendMessage(Msg.ok("NPC <white>" + a[1] + "</white> supprimé."));
        else s.sendMessage(Msg.err("Suppression en échec."));
    }

    private void cmdList(CommandSender s) {
        if (plugin.npcs().all().isEmpty()) {
            s.sendMessage(Msg.info("Aucun NPC."));
            return;
        }
        s.sendMessage(Msg.info("<gold>NPCs</gold> (" + plugin.npcs().all().size() + ")"));
        for (Npc npc : plugin.npcs().all().values()) {
            s.sendMessage(Msg.mm("<gray>#" + npc.id() + " <white>" + npc.displayName()
                    + "</white> <dark_gray>" + npc.world().getName()
                    + " " + (int) npc.currentLocation().getX()
                    + "/" + (int) npc.currentLocation().getY()
                    + "/" + (int) npc.currentLocation().getZ()
                    + (npc.wander() ? " <yellow>[wander]</yellow>" : "") + "</dark_gray>"));
        }
    }

    private void cmdSkin(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(Msg.err("Usage: /npc skin <pseudo> <joueur>")); return; }
        Npc npc = plugin.npcs().byName(a[1]);
        if (npc == null) { s.sendMessage(Msg.err("NPC introuvable.")); return; }
        s.sendMessage(Msg.info("Récupération du skin de <white>" + a[2] + "</white>…"));
        SkinFetcher.fetch(a[2]).thenAccept(skin -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (skin == null) { s.sendMessage(Msg.err("Skin introuvable.")); return; }
            plugin.npcs().reskin(npc, skin);
            s.sendMessage(Msg.ok("Skin mis à jour."));
        }));
    }

    private void cmdWander(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /npc wander <pseudo>")); return; }
        Npc npc = plugin.npcs().byName(a[1]);
        if (npc == null) { s.sendMessage(Msg.err("NPC introuvable.")); return; }
        plugin.npcs().toggleWander(npc);
        s.sendMessage(Msg.ok("Wander " + (npc.wander() ? "activé" : "désactivé") + "."));
    }

    private void cmdTp(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /npc tp <pseudo>")); return; }
        Npc npc = plugin.npcs().byName(a[1]);
        if (npc == null) { s.sendMessage(Msg.err("NPC introuvable.")); return; }
        p.teleport(npc.currentLocation());
        s.sendMessage(Msg.ok("Téléporté."));
    }

    private void cmdHere(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Joueur uniquement.")); return; }
        if (a.length < 2) { s.sendMessage(Msg.err("Usage: /npc here <pseudo>")); return; }
        Npc npc = plugin.npcs().byName(a[1]);
        if (npc == null) { s.sendMessage(Msg.err("NPC introuvable.")); return; }
        plugin.npcs().move(npc, p.getLocation());
        s.sendMessage(Msg.ok("NPC déplacé."));
    }

    private void cmdRename(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(Msg.err("Usage: /npc rename <pseudo> <nouveau>")); return; }
        Npc npc = plugin.npcs().byName(a[1]);
        if (npc == null) { s.sendMessage(Msg.err("NPC introuvable.")); return; }
        plugin.npcs().rename(npc, a[2]);
        s.sendMessage(Msg.ok("NPC renommé en <white>" + a[2] + "</white>."));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("smp.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String sub : List.of("create", "remove", "list", "skin", "wander", "tp", "here", "rename")) {
                if (sub.startsWith(a[0].toLowerCase())) out.add(sub);
            }
            return out;
        }
        if (a.length == 2) {
            String sub = a[0].toLowerCase();
            if (sub.equals("remove") || sub.equals("skin") || sub.equals("wander")
                    || sub.equals("tp") || sub.equals("here") || sub.equals("rename")) {
                for (Npc npc : plugin.npcs().all().values()) {
                    if (npc.displayName().toLowerCase().startsWith(a[1].toLowerCase())) {
                        out.add(npc.displayName());
                    }
                }
            }
        }
        return out;
    }
}
