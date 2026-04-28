package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds the duel queue and pairs players together. Lives only on the duel
 * server: lobby players who click the queue NPC are transferred here first
 * (cross-server transfer with inventory sync), then enqueue locally.
 *
 * Pairing strategy is dead-simple FIFO: oldest 2 players who picked the same
 * arena tag (or "any") get matched. ELO-based matchmaking can layer on later
 * by sorting buckets — Phase 4.
 */
public class DuelQueueManager implements Listener {

    /** Used by duel-server's plugin-message receiver to enqueue freshly-arrived players. */
    public static final String INTENT_META = "smp_duel_intent";

    public record Entry(UUID uuid, String name, String arenaPref, long enqueuedAt) {}

    private final SMPCore plugin;
    private final LinkedList<Entry> queue = new LinkedList<>();
    private final ConcurrentMap<UUID, Entry> byPlayer = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private final Random rng = new Random();

    public DuelQueueManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Pairing tick runs every second — fast enough that no one notices the
        // delay but cheap on a near-empty queue.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPair, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        synchronized (queue) {
            queue.clear();
            byPlayer.clear();
        }
    }

    public int size() {
        synchronized (queue) { return queue.size(); }
    }

    public boolean isQueued(UUID uuid) { return byPlayer.containsKey(uuid); }

    public List<Entry> snapshot() {
        synchronized (queue) { return new ArrayList<>(queue); }
    }

    public void enqueue(Player p, String arenaPref) {
        if (!plugin.isMainSurvival() && !"duels".equalsIgnoreCase(plugin.getServerType())) {
            // Lobby: transfer to duels first, then re-enqueue once they land
            // on the other side. We tag the player with metadata so the
            // duel-side join handler knows to push them straight into the queue.
            String norm = arenaPref == null ? "" : arenaPref;
            p.setMetadata(INTENT_META, new org.bukkit.metadata.FixedMetadataValue(plugin, norm));
            p.sendMessage(Msg.info("<gray>Transfert vers le serveur duels...</gray>"));
            plugin.getMessageChannel().sendTransfer(p, "duels");
            return;
        }

        if (isQueued(p.getUniqueId())) {
            p.sendMessage(Msg.err("Tu es déjà en file."));
            return;
        }
        if (plugin.duelMatches() != null && plugin.duelMatches().byPlayer(p.getUniqueId()) != null) {
            p.sendMessage(Msg.err("Tu es déjà en match."));
            return;
        }
        Entry e = new Entry(p.getUniqueId(), p.getName(),
                arenaPref == null || arenaPref.isBlank() ? null : arenaPref.toLowerCase(Locale.ROOT),
                System.currentTimeMillis());
        synchronized (queue) {
            queue.add(e);
            byPlayer.put(p.getUniqueId(), e);
        }
        p.sendMessage(Msg.info("<gold>En file</gold> <gray>(" + queue.size() + " en attente). " +
                "<white>/duel leave</white> pour quitter.</gray>"));
    }

    public void leave(Player p) {
        Entry removed;
        synchronized (queue) {
            removed = byPlayer.remove(p.getUniqueId());
            if (removed != null) queue.remove(removed);
        }
        p.sendMessage(removed != null
                ? Msg.ok("Sortie de la file.")
                : Msg.err("Tu n'étais pas en file."));
    }

    /** Called by JoinListener when a player marked with INTENT_META lands here. */
    public void enqueueOnArrival(Player p) {
        if (!p.hasMetadata(INTENT_META)) return;
        String pref = p.getMetadata(INTENT_META).isEmpty() ? "" : p.getMetadata(INTENT_META).get(0).asString();
        p.removeMetadata(INTENT_META, plugin);
        // Defer one tick so the player's location/world is settled.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            enqueue(p, pref.isEmpty() ? null : pref);
        }, 20L);
    }

    /**
     * On the dedicated `duels` backend, anyone who joins is here to fight —
     * auto-enqueue them after a short delay so they don't have to type a
     * command to start. Admins (smp.admin) and players who already have a
     * running match are skipped. The delay also gives auth/sync time to
     * settle their inventory before the matchmaker picks them.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!"duels".equalsIgnoreCase(plugin.getServerType())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            byPlayer.remove(p.getUniqueId()); // clear any stale state
            if (p.hasPermission("smp.admin") || p.hasPermission("smp.duel.bypass")) return;
            if (plugin.duelMatches() != null && plugin.duelMatches().byPlayer(p.getUniqueId()) != null) return;
            String pref = p.hasMetadata(INTENT_META) && !p.getMetadata(INTENT_META).isEmpty()
                    ? p.getMetadata(INTENT_META).get(0).asString() : null;
            if (p.hasMetadata(INTENT_META)) p.removeMetadata(INTENT_META, plugin);
            enqueue(p, pref != null && !pref.isBlank() ? pref : null);
        }, 40L);
    }

    private void tickPair() {
        if (plugin.duelArenas() == null || plugin.duelMatches() == null) return;
        if (size() < 2) return;
        Entry first;
        Entry partner;
        synchronized (queue) {
            // Find a pair: pick the oldest entry, then the oldest entry whose
            // arenaPref is null/equal/superset of the first's pref.
            first = queue.peek();
            if (first == null) return;
            partner = null;
            Iterator<Entry> it = queue.iterator();
            it.next(); // skip the head
            while (it.hasNext()) {
                Entry e = it.next();
                if (compatible(first.arenaPref, e.arenaPref)) { partner = e; break; }
            }
            if (partner == null) return;
            queue.remove(first);
            queue.remove(partner);
            byPlayer.remove(first.uuid);
            byPlayer.remove(partner.uuid);
        }

        Player a = Bukkit.getPlayer(first.uuid);
        Player b = Bukkit.getPlayer(partner.uuid);
        if (a == null || b == null) {
            // Re-queue whoever is still online so they don't lose their slot.
            if (a != null) enqueue(a, first.arenaPref);
            if (b != null) enqueue(b, partner.arenaPref);
            return;
        }
        DuelArena arena = pickArena(first.arenaPref != null ? first.arenaPref : partner.arenaPref);
        if (arena == null) {
            a.sendMessage(Msg.err("Aucune arène disponible — réessaie plus tard."));
            b.sendMessage(Msg.err("Aucune arène disponible — réessaie plus tard."));
            return;
        }
        plugin.duelMatches().start(arena, a, b);
    }

    private boolean compatible(String prefA, String prefB) {
        if (prefA == null || prefB == null) return true;
        return prefA.equalsIgnoreCase(prefB);
    }

    private DuelArena pickArena(String pref) {
        List<DuelArena> candidates = new ArrayList<>();
        for (DuelArena a : plugin.duelArenas().all()) {
            if (!a.enabled()) continue;
            if (plugin.duelArenas().usableSpawns(a).isEmpty()) continue;
            if (pref != null && !a.name().equalsIgnoreCase(pref)) continue;
            candidates.add(a);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(rng.nextInt(candidates.size()));
    }
}
