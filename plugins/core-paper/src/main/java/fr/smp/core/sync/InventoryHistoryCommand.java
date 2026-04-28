package fr.smp.core.sync;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.InvRollbackGUI;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /invrollback list|peek|apply|snap — admin tool on top of InventoryHistoryManager.
 *
 * list  <player> [limit]        → N most recent snapshots with index + timestamp + source
 * peek  <player> <index>        → shows item/armor/ender counts + xp/health summary
 * apply <player> <index>        → restores that snapshot (player must be online)
 * snap  <player>                → captures an on-demand snapshot right now
 */
public class InventoryHistoryCommand implements CommandExecutor, TabCompleter {

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final SMPCore plugin;

    public InventoryHistoryCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        InventoryHistoryManager mgr = plugin.invHistory();
        if (mgr == null || !mgr.isEnabled()) {
            sender.sendMessage(Msg.err("Historique d'inventaires désactivé sur ce serveur."));
            return true;
        }
        // /invrollback <joueur> → GUI (admin joueur uniquement)
        if (args.length == 1 && sender instanceof Player player
                && !List.of("list","peek","apply","snap").contains(args[0].toLowerCase(Locale.ROOT))) {
            UUID uuid = mgr.resolveUuid(args[0]);
            if (uuid == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return true; }
            InvRollbackGUI.open(plugin, player, uuid, args[0]);
            return true;
        }

        if (args.length == 0) { usage(sender); return true; }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "list" -> cmdList(sender, args, mgr);
            case "peek" -> cmdPeek(sender, args, mgr);
            case "apply" -> cmdApply(sender, args, mgr);
            case "snap" -> cmdSnap(sender, args, mgr);
            default -> { usage(sender); yield true; }
        };
    }

    private void usage(CommandSender s) {
        s.sendMessage(Msg.info("<gray>/invrollback list <joueur> [limit]</gray>"));
        s.sendMessage(Msg.info("<gray>/invrollback peek <joueur> <index></gray>"));
        s.sendMessage(Msg.info("<gray>/invrollback apply <joueur> <index></gray>"));
        s.sendMessage(Msg.info("<gray>/invrollback snap <joueur></gray>"));
    }

    private boolean cmdList(CommandSender sender, String[] args, InventoryHistoryManager mgr) {
        if (args.length < 2) { sender.sendMessage(Msg.err("Usage: /invrollback list <joueur> [limit]")); return true; }
        UUID uuid = mgr.resolveUuid(args[1]);
        if (uuid == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return true; }
        int limit = 20;
        if (args.length >= 3) {
            try { limit = Math.max(1, Math.min(100, Integer.parseInt(args[2]))); }
            catch (NumberFormatException e) { sender.sendMessage(Msg.err("Limit invalide.")); return true; }
        }
        List<InventoryHistoryManager.Entry> rows = mgr.list(uuid, limit);
        if (rows.isEmpty()) {
            sender.sendMessage(Msg.info("<gray>Aucun snapshot pour ce joueur.</gray>"));
            return true;
        }
        sender.sendMessage(Msg.info("<gray>Snapshots de <white>" + args[1] + "</white> (plus récent en tête) :</gray>"));
        for (int i = 0; i < rows.size(); i++) {
            var e = rows.get(i);
            sender.sendMessage(Msg.info(String.format(
                    "<gray>#%-2d  id=<yellow>%d</yellow>  <white>%s</white>  <dark_gray>[%s]</dark_gray>  <gray>%s</gray></gray>",
                    i, e.id(), FMT.format(new Date(e.createdAt())), e.source(), e.server())));
        }
        return true;
    }

    private boolean cmdPeek(CommandSender sender, String[] args, InventoryHistoryManager mgr) {
        if (args.length < 3) { sender.sendMessage(Msg.err("Usage: /invrollback peek <joueur> <index>")); return true; }
        InventoryHistoryManager.Entry entry = resolveEntry(sender, mgr, args[1], args[2]);
        if (entry == null) return true;
        YamlConfiguration yaml = mgr.load(entry.id());
        if (yaml == null) { sender.sendMessage(Msg.err("Snapshot illisible.")); return true; }

        int invCount = countItems(yaml.getList("inventory.contents"));
        int armorCount = countItems(yaml.getList("inventory.armor"));
        int enderCount = countItems(yaml.getList("enderchest"));
        int xpLevel = yaml.getInt("xp.level", 0);
        double health = yaml.getDouble("stats.health", 20.0);
        int food = yaml.getInt("stats.food", 20);
        String sourceServer = yaml.getString("source-server", "?");

        sender.sendMessage(Msg.info("<gray>Snapshot id=<yellow>" + entry.id() + "</yellow> "
                + "pris le <white>" + FMT.format(new Date(entry.createdAt())) + "</white> "
                + "(source <white>" + entry.source() + "</white>, serveur <white>" + sourceServer + "</white>)</gray>"));
        sender.sendMessage(Msg.info(String.format(
                "<gray>Inv: <white>%d</white> items   Armure: <white>%d</white>   Ender: <white>%d</white></gray>",
                invCount, armorCount, enderCount)));
        sender.sendMessage(Msg.info(String.format(
                "<gray>Niveau XP: <white>%d</white>   HP: <white>%.1f</white>   Food: <white>%d</white></gray>",
                xpLevel, health, food)));
        return true;
    }

    private boolean cmdApply(CommandSender sender, String[] args, InventoryHistoryManager mgr) {
        if (args.length < 3) { sender.sendMessage(Msg.err("Usage: /invrollback apply <joueur> <index>")); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Msg.err("Le joueur doit être en ligne sur ce serveur pour appliquer un rollback."));
            return true;
        }
        InventoryHistoryManager.Entry entry = resolveEntryFor(sender, mgr, target.getUniqueId(), args[1], args[2]);
        if (entry == null) return true;
        String actor = sender instanceof Player p ? p.getName() : "console";
        boolean ok = mgr.applyTo(entry.id(), target, actor);
        if (!ok) {
            sender.sendMessage(Msg.err("Échec du rollback (snapshot illisible ou corrompu)."));
            return true;
        }
        sender.sendMessage(Msg.ok("<green>Rollback appliqué à <yellow>" + target.getName()
                + "</yellow> (snapshot id=" + entry.id() + ", "
                + FMT.format(new Date(entry.createdAt())) + "). Un snapshot PREAPPLY a été pris.</green>"));
        target.sendMessage(Msg.info("<yellow>Ton inventaire a été restauré par un administrateur.</yellow>"));
        return true;
    }

    private boolean cmdSnap(CommandSender sender, String[] args, InventoryHistoryManager mgr) {
        if (args.length < 2) { sender.sendMessage(Msg.err("Usage: /invrollback snap <joueur>")); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Msg.err("Le joueur doit être en ligne pour un snapshot manuel."));
            return true;
        }
        String actor = sender instanceof Player p ? p.getName() : "console";
        mgr.snapshotManual(target, actor);
        sender.sendMessage(Msg.ok("<green>Snapshot manuel de <yellow>" + target.getName() + "</yellow> pris.</green>"));
        return true;
    }

    private InventoryHistoryManager.Entry resolveEntry(CommandSender sender,
                                                       InventoryHistoryManager mgr,
                                                       String playerArg, String indexArg) {
        UUID uuid = mgr.resolveUuid(playerArg);
        if (uuid == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return null; }
        return resolveEntryFor(sender, mgr, uuid, playerArg, indexArg);
    }

    private InventoryHistoryManager.Entry resolveEntryFor(CommandSender sender,
                                                          InventoryHistoryManager mgr,
                                                          UUID uuid,
                                                          String playerArg, String indexArg) {
        // Accept either an index (from /invrollback list) or an explicit id=<n>.
        if (indexArg.startsWith("id=")) {
            try {
                long id = Long.parseLong(indexArg.substring(3));
                InventoryHistoryManager.Entry e = mgr.meta(id);
                if (e == null || !e.uuid().equals(uuid)) {
                    sender.sendMessage(Msg.err("Snapshot introuvable pour ce joueur."));
                    return null;
                }
                return e;
            } catch (NumberFormatException nfe) {
                sender.sendMessage(Msg.err("Id invalide."));
                return null;
            }
        }
        int idx;
        try { idx = Integer.parseInt(indexArg); }
        catch (NumberFormatException e) {
            sender.sendMessage(Msg.err("Index invalide. Utilise /invrollback list " + playerArg + " d'abord."));
            return null;
        }
        List<InventoryHistoryManager.Entry> rows = mgr.list(uuid, Math.max(idx + 1, 100));
        if (idx < 0 || idx >= rows.size()) {
            sender.sendMessage(Msg.err("Index hors plage (0.." + (rows.size() - 1) + ")."));
            return null;
        }
        return rows.get(idx);
    }

    private int countItems(List<?> raw) {
        if (raw == null) return 0;
        int n = 0;
        for (Object o : raw) {
            if (o instanceof ItemStack it && !it.getType().isAir()) n++;
        }
        return n;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) {
            return filter(List.of("list", "peek", "apply", "snap"), args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> src, String pref) {
        String p = pref.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : src) if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        return out;
    }
}
