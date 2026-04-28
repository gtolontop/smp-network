package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lifecycle of duel matches: copy template world → load → teleport players →
 * end on death / disconnect / timeout → cleanup world.
 *
 * World layout on disk is `&lt;worldContainer&gt;/match_&lt;id&gt;_&lt;arena&gt;/`. The match's
 * Bukkit world name shares the directory name, which is intentional — block
 * protection (DuelArenaListener) keys arenas by Bukkit world name, but the
 * arena lookup for match worlds happens through DuelMatchManager#arenaFor.
 */
public class DuelMatchManager {

    /** Soft cap; enough headroom for an unattended match without leaking the world. */
    private static final long MATCH_TIMEOUT_MS = 10 * 60 * 1000L;
    /** Countdown before fighting starts. */
    private static final int START_COUNTDOWN_TICKS = 60; // 3s

    private final SMPCore plugin;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, DuelMatch> matches = new ConcurrentHashMap<>();
    /** Per-instance-world reverse lookup so listeners can answer "what match is this player in?" */
    private final Map<String, DuelMatch> byWorld = new ConcurrentHashMap<>();
    private final Map<UUID, DuelMatch> byPlayer = new ConcurrentHashMap<>();
    private final Map<Long, BukkitTask> timeouts = new HashMap<>();
    private final Map<Long, UUID> pendingSurrenders = new ConcurrentHashMap<>();

    public DuelMatchManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public DuelMatch byId(long id) { return matches.get(id); }
    public DuelMatch byWorld(String worldName) { return byWorld.get(worldName); }
    public DuelMatch byPlayer(UUID uuid) { return byPlayer.get(uuid); }
    public java.util.Collection<DuelMatch> all() { return matches.values(); }

    /**
     * Try to start a match between A and B inside the given arena. Returns the
     * match if everything queued, else null.
     *
     * Both players must be online and on this server. World copy runs async, so
     * the caller can't assume the players are teleported by the time this returns.
     */
    public DuelMatch start(DuelArena arena, Player a, Player b) {
        if (arena == null || a == null || b == null) return null;
        if (!arena.enabled()) {
            a.sendMessage(Msg.err("Cette arène est désactivée."));
            return null;
        }
        if (plugin.duelArenas().usableSpawns(arena).isEmpty()) {
            a.sendMessage(Msg.err("Cette arène n'a aucune paire de spawns complète."));
            return null;
        }
        if (byPlayer.containsKey(a.getUniqueId()) || byPlayer.containsKey(b.getUniqueId())) {
            a.sendMessage(Msg.err("Un des joueurs est déjà en duel."));
            return null;
        }

        long id = nextId.getAndIncrement();
        String worldName = "match_" + id + "_" + arena.name().toLowerCase(Locale.ROOT);
        DuelMatch match = new DuelMatch(id, arena, worldName,
                a.getUniqueId(), a.getName(), b.getUniqueId(), b.getName());
        matches.put(id, match);
        byPlayer.put(a.getUniqueId(), match);
        byPlayer.put(b.getUniqueId(), match);

        Player[] both = { a, b };
        for (Player p : both) {
            p.sendMessage(Msg.info("<gold>Duel</gold> <gray>contre</gray> <white>" +
                    (p == a ? b.getName() : a.getName()) + "</white> <gray>— préparation de l'arène...</gray>"));
        }

        // Copy the template world off-thread, then back to main thread for createWorld + teleport.
        File container = Bukkit.getWorldContainer();
        File source = new File(container, arena.world());
        File dest = new File(container, worldName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = copyWorldDir(source.toPath(), dest.toPath());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!ok) {
                    abort(match, "<red>Impossible de copier le monde template.</red>");
                    return;
                }
                if (!loadAndTeleport(match)) {
                    abort(match, "<red>Impossible de charger l'arène.</red>");
                    return;
                }
                scheduleStartCountdown(match);
                BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> end(match, null, "<gray>Match expiré (timeout).</gray>"),
                        MATCH_TIMEOUT_MS / 50);
                timeouts.put(match.id(), timeout);
            });
        });
        return match;
    }

    @SuppressWarnings("removal") // GameRule constants will be replaced by registry lookups in a future Paper release.
    private boolean loadAndTeleport(DuelMatch match) {
        WorldCreator wc = new WorldCreator(match.worldName());
        // The copied folder already contains level.dat — Bukkit reads its
        // generator settings from there. We still set a default generator to
        // a normal one so that loaded chunks aren't regenerated.
        World w = wc.createWorld();
        if (w == null) return false;
        w.setAutoSave(false);
        w.setDifficulty(org.bukkit.Difficulty.NORMAL);
        w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false);
        w.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
        w.setStorm(false);
        w.setThundering(false);
        w.setTime(6000);
        byWorld.put(match.worldName(), match);

        Player a = match.onlineA();
        Player b = match.onlineB();
        if (a == null || b == null) return false;

        Location la = match.spawnFor(match.playerA());
        Location lb = match.spawnFor(match.playerB());
        if (la == null || lb == null) return false;

        a.teleportAsync(la);
        b.teleportAsync(lb);
        // Freeze them for the countdown so neither can swing first / break the other.
        for (Player p : new Player[]{a, b}) {
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setSaturation(20);
            p.setFireTicks(0);
            p.setNoDamageTicks(START_COUNTDOWN_TICKS + 20);
            p.setWalkSpeed(0f);
            p.setFlySpeed(0f);
        }
        return true;
    }

    private void scheduleStartCountdown(DuelMatch match) {
        match.setState(DuelMatch.State.STARTING);
        // 3 → 2 → 1 → GO, one per second.
        for (int i = 3; i >= 1; i--) {
            final int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (matches.get(match.id()) != match) return;
                Player a = match.onlineA();
                Player b = match.onlineB();
                if (a != null) a.sendMessage(Msg.mm("<gold><bold>" + n + "</bold></gold>"));
                if (b != null) b.sendMessage(Msg.mm("<gold><bold>" + n + "</bold></gold>"));
            }, (3 - i) * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (matches.get(match.id()) != match) return;
            match.setState(DuelMatch.State.FIGHTING);
            for (UUID uuid : new UUID[]{match.playerA(), match.playerB()}) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                p.setWalkSpeed(0.2f);
                p.setFlySpeed(0.1f);
                p.setNoDamageTicks(0);
                p.sendMessage(Msg.mm("<green><bold>GO !</bold></green>"));
            }
        }, START_COUNTDOWN_TICKS);
    }

    /** Called by listener when one of the participants dies. */
    public void handleDeath(DuelMatch match, Player victim) {
        if (match.state() == DuelMatch.State.ENDING) return;
        UUID winnerId = match.otherSide(victim.getUniqueId());
        match.setOutcome(winnerId, victim.getUniqueId(), false);
        Player winner = winnerId != null ? Bukkit.getPlayer(winnerId) : null;
        String winnerName = winner != null ? winner.getName()
                : (match.playerA().equals(winnerId) ? match.nameA() : match.nameB());
        end(match, winnerId, "<gold>" + winnerName + "</gold> <gray>remporte le duel.</gray>");
    }

    /** Called when one of the participants quits / leaves the server. */
    public void handleQuit(DuelMatch match, UUID quitter) {
        if (match.state() == DuelMatch.State.ENDING) return;
        UUID winnerId = match.otherSide(quitter);
        match.setOutcome(winnerId, quitter, true);
        String winnerName = match.playerA().equals(winnerId) ? match.nameA() : match.nameB();
        end(match, winnerId, "<gold>" + winnerName + "</gold> <gray>gagne par forfait (déconnexion).</gray>");
    }

    /**
     * Conclude a match: announce, teleport survivors back, schedule world cleanup.
     * Idempotent — repeated calls are no-ops.
     */
    public void end(DuelMatch match, UUID winnerId, String reason) {
        if (match.state() == DuelMatch.State.ENDING) return;
        match.setState(DuelMatch.State.ENDING);
        pendingSurrenders.remove(match.id());
        BukkitTask t = timeouts.remove(match.id());
        if (t != null) t.cancel();

        // Notify both players and any spectators.
        for (UUID uuid : new UUID[]{match.playerA(), match.playerB()}) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(Msg.info(reason != null ? reason : "<gray>Match terminé.</gray>"));
        }
        for (UUID s : match.spectators()) {
            Player p = Bukkit.getPlayer(s);
            if (p != null) p.sendMessage(Msg.info(reason != null ? reason : "<gray>Match terminé.</gray>"));
        }

        // Reward + ELO pipeline lives in DuelRewardManager (Phase 4) — call it
        // here so the duel server can credit saph + update ELO; falls through
        // gracefully if the manager isn't initialised.
        if (winnerId != null && plugin.duelRewards() != null) {
            UUID loser = match.otherSide(winnerId);
            plugin.duelRewards().applyOutcome(winnerId, loser);
        }

        // Teleport everyone out so we can unload the world cleanly.
        Location fallback = plugin.spawns().hub();
        if (fallback == null) fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
        Location to = fallback;
        for (UUID uuid : new UUID[]{match.playerA(), match.playerB()}) {
            byPlayer.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setWalkSpeed(0.2f);
                p.setFlySpeed(0.1f);
                p.teleportAsync(to);
            }
        }
        for (UUID s : new java.util.HashSet<>(match.spectators())) {
            Player p = Bukkit.getPlayer(s);
            if (p != null) {
                p.setGameMode(GameMode.SURVIVAL);
                p.teleportAsync(to);
            }
        }

        // Give the engine a couple of ticks to finish processing teleports
        // before we yank the world out from under it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(match), 40L);
    }

    private void cleanup(DuelMatch match) {
        World w = match.world();
        if (w != null) {
            // Boot anyone who slipped in during the teardown window.
            for (Player p : w.getPlayers()) {
                Location to = plugin.spawns().hub();
                if (to == null) to = Bukkit.getWorlds().get(0).getSpawnLocation();
                p.teleportAsync(to);
            }
            Bukkit.unloadWorld(w, false);
        }
        byWorld.remove(match.worldName());
        matches.remove(match.id());

        // Delete the directory off-thread to keep the main loop snappy.
        File dir = new File(Bukkit.getWorldContainer(), match.worldName());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteRecursive(dir.toPath()));
    }

    private void abort(DuelMatch match, String reasonMm) {
        for (UUID uuid : new UUID[]{match.playerA(), match.playerB()}) {
            byPlayer.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(Msg.info(reasonMm));
        }
        matches.remove(match.id());
        // World may have been partially copied — try to clean.
        File dir = new File(Bukkit.getWorldContainer(), match.worldName());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteRecursive(dir.toPath()));
    }

    /* ----------------------------- Surrender ----------------------------- */

    public boolean requestSurrender(Player requester) {
        DuelMatch m = byPlayer.get(requester.getUniqueId());
        if (m == null || m.state() != DuelMatch.State.FIGHTING) return false;

        long matchId = m.id();
        UUID requesterId = requester.getUniqueId();
        UUID otherId = m.otherSide(requesterId);

        UUID existing = pendingSurrenders.get(matchId);
        if (existing != null) {
            if (existing.equals(requesterId)) {
                requester.sendMessage(Msg.mm("<gray>Tu as déjà proposé une capitulation."));
                return true;
            }
            // Other already proposed → mutual accept.
            pendingSurrenders.remove(matchId);
            executeSurrender(m);
            return true;
        }

        pendingSurrenders.put(matchId, requesterId);

        Player otherP = Bukkit.getPlayer(otherId);
        if (otherP != null) {
            otherP.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<gold>" + requester.getName() + "</gold> <gray>propose de capituler " +
                    "<dark_gray>(-8 ELO chacun, pas de gagnant)</dark_gray>. " +
                    "<green><click:run_command:'/duel surrender'>[Accepter]</click></green> " +
                    "<red><click:run_command:'/duel surrender decline'>[Refuser]</click></red>"));
        }
        requester.sendMessage(Msg.ok("Proposition envoyée à <white>" +
                (otherP != null ? otherP.getName() : "l'adversaire") + "</white>. <gray>Expire dans 30s.</gray>"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingSurrenders.remove(matchId, requesterId)) {
                Player req = Bukkit.getPlayer(requesterId);
                if (req != null) req.sendMessage(Msg.mm("<gray>Proposition de capitulation expirée."));
                Player oth = Bukkit.getPlayer(otherId);
                if (oth != null) oth.sendMessage(Msg.mm("<gray>La proposition de capitulation a expiré."));
            }
        }, 30 * 20L);
        return true;
    }

    public boolean declineSurrender(DuelMatch m, Player decliner) {
        UUID existing = pendingSurrenders.get(m.id());
        if (existing == null || existing.equals(decliner.getUniqueId())) return false;
        pendingSurrenders.remove(m.id());
        decliner.sendMessage(Msg.mm("<gray>Capitulation refusée."));
        Player requester = Bukkit.getPlayer(existing);
        if (requester != null) requester.sendMessage(Msg.mm("<red>" + decliner.getName() + " a refusé la capitulation.</red>"));
        return true;
    }

    private void executeSurrender(DuelMatch m) {
        if (plugin.duelRewards() != null) {
            plugin.duelRewards().applySurrender(m.playerA(), m.playerB());
        }
        end(m, null, "<gray>Capitulation mutuelle.</gray>");
    }

    public void shutdown() {
        // End every running match without rewards (server going down).
        for (DuelMatch m : new java.util.ArrayList<>(matches.values())) {
            try {
                end(m, null, "<gray>Serveur en cours d'arrêt.</gray>");
            } catch (Throwable ignored) {}
        }
    }

    /* ----------------------------- File I/O ----------------------------- */

    /**
     * Recursive copy of a world directory. Skips uid.dat (Bukkit assigns a
     * fresh UUID on createWorld) and session.lock (Mojang's per-process lock
     * file). Copying session.lock would otherwise inherit the template's
     * lock state and refuse to load.
     */
    private boolean copyWorldDir(Path source, Path dest) {
        if (!Files.isDirectory(source)) {
            plugin.getLogger().warning("Template world dir does not exist: " + source);
            return false;
        }
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                    Path rel = source.relativize(dir);
                    Path target = dest.resolve(rel.toString());
                    Files.createDirectories(target);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    String name = file.getFileName().toString();
                    if (name.equals("uid.dat") || name.equals("session.lock")) return FileVisitResult.CONTINUE;
                    Path rel = source.relativize(file);
                    Path target = dest.resolve(rel.toString());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Copy world failed: " + e.getMessage());
            return false;
        }
    }

    private void deleteRecursive(Path path) {
        if (!Files.exists(path)) return;
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    try { Files.delete(file); } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, java.io.IOException exc) throws java.io.IOException {
                    try { Files.delete(dir); } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Cleanup match dir failed: " + e.getMessage());
        }
    }
}
