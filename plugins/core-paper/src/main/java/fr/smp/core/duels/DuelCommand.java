package fr.smp.core.duels;

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
import java.util.Locale;

/**
 * Player-facing entry point. The admin-only "start" subcommand is the bypass
 * used in Phase 2 testing — the queue-based flow lands in Phase 3 alongside
 * the lobby NPC.
 */
public class DuelCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public DuelCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> handleStart(sender, args);
            case "end" -> handleEnd(sender, args);
            case "list" -> handleList(sender);
            case "stats" -> handleStats(sender, args);
            case "top", "leaderboard", "lb" -> handleTop(sender);
            case "queue" -> handleQueue(sender, args);
            case "leave" -> handleLeave(sender);
            case "spectate" -> handleSpectate(sender, args);
            case "surrender" -> handleSurrender(sender, args);
            case "npc" -> handleNpcSetup(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.info("<gold>/duel</gold>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duel queue [arène]</white> — rejoindre la file"));
        s.sendMessage(Msg.mm("<gray> • <white>/duel surrender</white> — proposer/accepter une capitulation mutuelle <dark_gray>(-8 ELO chacun)</dark_gray>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duel leave</white> — quitter la file"));
        s.sendMessage(Msg.mm("<gray> • <white>/duel spectate [match-id]</white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duel stats [joueur]</white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duel top</white> — top 10 ELO"));
        if (s.hasPermission("smp.admin")) {
            s.sendMessage(Msg.mm("<dark_gray> • <white>/duel start <a> <b> <arène></white> (admin)"));
            s.sendMessage(Msg.mm("<dark_gray> • <white>/duel end <match-id></white> (admin)"));
            s.sendMessage(Msg.mm("<dark_gray> • <white>/duel list</white> (admin)"));
            s.sendMessage(Msg.mm("<dark_gray> • <white>/duel npc</white> — pose le PNJ + hologramme à ta position (admin)"));
        }
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Msg.err("/duel start <a> <b> <arène>"));
            return;
        }
        Player a = Bukkit.getPlayerExact(args[1]);
        Player b = Bukkit.getPlayerExact(args[2]);
        if (a == null || !a.isOnline()) { sender.sendMessage(Msg.err("Joueur A introuvable.")); return; }
        if (b == null || !b.isOnline()) { sender.sendMessage(Msg.err("Joueur B introuvable.")); return; }
        if (a.getUniqueId().equals(b.getUniqueId())) { sender.sendMessage(Msg.err("Tu ne peux pas duel toi-même.")); return; }
        DuelArena arena = plugin.duelArenas().get(args[3]);
        if (arena == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        DuelMatch m = plugin.duelMatches().start(arena, a, b);
        if (m == null) {
            sender.sendMessage(Msg.err("Démarrage refusé (voir avertissements précédents)."));
            return;
        }
        sender.sendMessage(Msg.ok("Match #" + m.id() + " démarré <gray>(" + a.getName() + " vs " + b.getName() + ")</gray>."));
    }

    private void handleEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return; }
        if (args.length < 2) { sender.sendMessage(Msg.err("/duel end <match-id>")); return; }
        long id;
        try { id = Long.parseLong(args[1]); } catch (NumberFormatException e) { sender.sendMessage(Msg.err("Id invalide.")); return; }
        DuelMatch m = plugin.duelMatches().byId(id);
        if (m == null) { sender.sendMessage(Msg.err("Match introuvable.")); return; }
        plugin.duelMatches().end(m, null, "<gray>Match terminé par admin.</gray>");
        sender.sendMessage(Msg.ok("Match terminé."));
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return; }
        sender.sendMessage(Msg.info("<gold>Matchs en cours</gold> <gray>(" + plugin.duelMatches().all().size() + ")</gray>"));
        for (DuelMatch m : plugin.duelMatches().all()) {
            sender.sendMessage(Msg.mm("<gray> • #" + m.id() + " <white>" + m.nameA() + "</white> vs <white>" +
                    m.nameB() + "</white> <dark_gray>" + m.arena().name() + " (" + m.state() + ")</dark_gray>"));
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (plugin.duelRewards() == null) {
            sender.sendMessage(Msg.err("Stats indisponibles."));
            return;
        }
        java.util.UUID uuid;
        String label;
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                uuid = target.getUniqueId();
                label = target.getName();
            } else {
                java.util.UUID resolved = plugin.players().resolveUuid(args[1]);
                if (resolved == null) {
                    sender.sendMessage(Msg.err("Joueur introuvable."));
                    return;
                }
                fr.smp.core.data.PlayerData d = plugin.players().loadOffline(resolved);
                uuid = resolved;
                label = d != null ? d.name() : args[1];
            }
        } else if (sender instanceof Player p) {
            uuid = p.getUniqueId();
            label = p.getName();
        } else {
            sender.sendMessage(Msg.err("/duel stats <joueur>"));
            return;
        }
        DuelRewardManager.DuelStats st = plugin.duelRewards().statsOf(uuid);
        sender.sendMessage(Msg.info("<gold>Stats duel — <white>" + label + "</white></gold>"));
        sender.sendMessage(Msg.mm("<gray> ELO: <white>" + st.elo() + "</white></gray>"));
        sender.sendMessage(Msg.mm("<gray> Wins: <green>" + st.wins() + "</green> / Losses: <red>" + st.losses() + "</red></gray>"));
        int total = st.wins() + st.losses();
        double wr = total == 0 ? 0 : (st.wins() * 100.0 / total);
        sender.sendMessage(Msg.mm("<gray> Winrate: <white>" + String.format("%.1f", wr) + "%</white></gray>"));
        sender.sendMessage(Msg.mm("<gray> Streak: <gold>" + st.streak() + "</gold> <dark_gray>(best " + st.bestStreak() + ")</dark_gray></gray>"));
    }

    private void handleTop(CommandSender sender) {
        if (plugin.duelRewards() == null) { sender.sendMessage(Msg.err("Stats indisponibles.")); return; }
        java.util.List<DuelRewardManager.DuelStats> top = plugin.duelRewards().top(10);
        sender.sendMessage(Msg.info("<gold>Top 10 ELO duel</gold>"));
        int rank = 1;
        for (DuelRewardManager.DuelStats s : top) {
            sender.sendMessage(Msg.mm("<gray> " + rank + ". <white>" + s.name() + "</white> <dark_gray>—</dark_gray> <gold>" +
                    s.elo() + "</gold> ELO <dark_gray>(" + s.wins() + "W/" + s.losses() + "L)</dark_gray>"));
            rank++;
        }
        if (top.isEmpty()) sender.sendMessage(Msg.mm("<gray>Pas encore de stats.</gray>"));
    }

    /**
     * Admin helper: places a duel-marked NPC + a 3-line hologram at the
     * sender's location. The NPC's display name contains "DUELS" so the
     * click injector picks it up; the hologram floats just above for the
     * "fight other players / no keep inventory" warning seen on Donut SMP.
     */
    private void handleNpcSetup(CommandSender sender) {
        if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (plugin.npcs() == null || plugin.holograms() == null) {
            p.sendMessage(Msg.err("NPC ou hologrammes indisponibles."));
            return;
        }
        org.bukkit.Location loc = p.getLocation();
        // Spawn the NPC. Skin is left default — admin can /npc skin <id> ... after.
        plugin.npcs().create(loc, "<red>DUELS</red>", null, false);
        // Hologram lines stacked vertically above the NPC. We create then push
        // the line set so we don't have to extend HologramManager for a single
        // bulk-create call.
        org.bukkit.Location holoLoc = loc.clone().add(0, 2.6, 0);
        String holoName = "duel_hub_" + (System.currentTimeMillis() / 1000);
        fr.smp.core.holograms.Hologram h = plugin.holograms().create(holoName, holoLoc);
        if (h != null) {
            plugin.holograms().setLines(h, java.util.List.of(
                    "<red><bold>DUELS</bold></red>",
                    "<gray>Fight other players!</gray>",
                    "",
                    "<yellow>Warning:</yellow>",
                    "<gold>No Keep Inventory</gold>"));
        }
        p.sendMessage(Msg.ok("PNJ + hologramme placés. <gray>Clic droit dessus pour ouvrir la GUI.</gray>"));
    }

    private void handleSurrender(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (args.length >= 2 && args[1].equalsIgnoreCase("decline")) {
            DuelMatch m = plugin.duelMatches().byPlayer(p.getUniqueId());
            if (m == null) { p.sendMessage(Msg.err("Tu n'es pas dans un match.")); return; }
            if (!plugin.duelMatches().declineSurrender(m, p)) {
                p.sendMessage(Msg.err("Aucune proposition en attente."));
            }
            return;
        }
        if (!plugin.duelMatches().requestSurrender(p)) {
            p.sendMessage(Msg.err("Tu n'es pas dans un match en cours de combat."));
        }
    }

    private void handleQueue(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (plugin.duelQueue() == null) {
            p.sendMessage(Msg.err("File d'attente indisponible sur ce serveur."));
            return;
        }
        plugin.duelQueue().enqueue(p, args.length >= 2 ? args[1] : null);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (plugin.duelQueue() == null) {
            p.sendMessage(Msg.err("File d'attente indisponible."));
            return;
        }
        plugin.duelQueue().leave(p);
    }

    private void handleSpectate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (plugin.duelMatches() == null) { p.sendMessage(Msg.err("Matchs indisponibles.")); return; }
        if (args.length < 2) {
            // Random pick: spectate the first running match.
            DuelMatch m = plugin.duelMatches().all().stream().findFirst().orElse(null);
            if (m == null) { p.sendMessage(Msg.err("Aucun match en cours.")); return; }
            spectate(p, m);
            return;
        }
        long id;
        try { id = Long.parseLong(args[1]); } catch (NumberFormatException e) { p.sendMessage(Msg.err("Id invalide.")); return; }
        DuelMatch m = plugin.duelMatches().byId(id);
        if (m == null) { p.sendMessage(Msg.err("Match introuvable.")); return; }
        spectate(p, m);
    }

    private void spectate(Player p, DuelMatch m) {
        if (m.world() == null) { p.sendMessage(Msg.err("Match en cours de chargement, retente.")); return; }
        m.spectators().add(p.getUniqueId());
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        p.teleportAsync(m.arena().center().clone()
                .toVector().toLocation(m.world(), m.arena().center().getYaw(), m.arena().center().getPitch()));
        p.sendMessage(Msg.ok("Spectate <gold>match #" + m.id() + "</gold> <gray>(" + m.nameA() + " vs " + m.nameB() + ")</gray>."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> base = new ArrayList<>(List.of("queue", "leave", "spectate", "stats", "top", "surrender"));
            if (sender.hasPermission("smp.admin")) {
                base.add("start");
                base.add("end");
                base.add("list");
                base.add("npc");
            }
            List<String> out = new ArrayList<>();
            for (String s : base) if (s.startsWith(pref)) out.add(s);
            return out;
        }
        return List.of();
    }
}
