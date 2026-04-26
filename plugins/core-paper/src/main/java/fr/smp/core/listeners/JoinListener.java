package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public class JoinListener implements Listener {

    private final SMPCore plugin;

    public JoinListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        event.joinMessage(null);

        PlayerData data = plugin.players().loadOrCreate(p.getUniqueId(), p.getName());
        plugin.logs().log(LogCategory.JOIN, p, "join (new=" +
                (data.firstJoin() >= System.currentTimeMillis()/1000 - 5) + ")");

        if (plugin.permissions() != null) plugin.permissions().apply(p);

        // If auth is loaded and the player is not yet authenticated, defer
        // teleport / world placement until /login or /register completes —
        // otherwise an unauthenticated cracker would be RTP'd to a real
        // player's coordinates while frozen.
        if (plugin.auth() != null && !plugin.auth().isAuthenticated(p)) {
            // Scoreboard / tab / nametags also wait for auth — the player
            // is invisible/blind anyway.
            return;
        }
        runJoinSetup(p, data);
    }

    /**
     * Post-auth join setup. Called either directly from onJoin() (premium
     * auto-auth) or from {@link fr.smp.core.auth.AuthManager} once a cracked
     * player completes /login or /register.
     */
    public void runJoinSetup(Player p, PlayerData data) {
        // Pending cross-server teleport wins over any other auto-tp behavior.
        long now = System.currentTimeMillis();
        PendingTeleportManager.Pending pending = plugin.pendingTp() != null
                ? plugin.pendingTp().peek(p.getUniqueId()) : null;
        boolean pendingFresh = pending != null
                && now - pending.createdAt() < 60_000;
        boolean pendingForThisBoot = pending != null
                && pending.createdAt() >= plugin.getStartedAtMillis();
        boolean pendingForThisServer = pending != null
                && pending.targets(plugin.getServerType());
        boolean handledPending = false;
        if (pendingFresh) {
            if (pendingForThisBoot && pendingForThisServer) {
                plugin.pendingTp().consume(p.getUniqueId());
                applyPending(p, pending);
                handledPending = true;
            } else {
                plugin.pendingTp().consume(p.getUniqueId());
            }
        } else if (pending != null && plugin.pendingTp() != null) {
            plugin.pendingTp().consume(p.getUniqueId());
        }

        if (handledPending) {
            // A valid pending transfer already decided the destination.
        } else if (plugin.isLobby()) {
            applyLobbyMode(p);
            Location hub = plugin.spawns().hub();
            if (hub != null) p.teleportAsync(hub);
        } else if (plugin.isPtr()) {
            applyPtrMode(p);
        } else {
            applySurvivalMode(p);
            if (!data.survivalJoined()) {
                String worldName = plugin.getConfig().getString("rtp.default-world", "world");
                World w = plugin.resolveWorld(worldName, World.Environment.NORMAL);
                if (w != null) {
                    p.sendMessage(Msg.info("<aqua>Bienvenue ! Téléportation aléatoire...</aqua>"));
                    plugin.rtp().teleport(p, w, false);
                } else {
                    Location s = plugin.spawns().spawn();
                    if (s != null) p.teleportAsync(s);
                }
            } else if (data.hasLastLocation()) {
                World lw = resolveStoredWorld(data.lastWorld());
                if (lw != null) {
                    Location last = new Location(lw, data.lastX(), data.lastY(), data.lastZ(),
                            data.lastYaw(), data.lastPitch());
                    p.teleportAsync(last);
                } else {
                    Location s = plugin.spawns().spawn();
                    if (s != null) p.teleportAsync(s);
                }
            }
        }

        if (plugin.isMainSurvival()) data.setSurvivalJoined(true);

        if (plugin.scoreboard() != null) plugin.scoreboard().apply(p);
        if (plugin.tabList() != null) plugin.tabList().update(p);
        if (plugin.nametags() != null) plugin.nametags().refresh(p);
        if (plugin.hunted() != null) plugin.hunted().refreshOnJoin(p);
        if (plugin.fullbright() != null) plugin.fullbright().refresh(p);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            var notes = plugin.auction().consumeSoldNotifications(p.getUniqueId());
            for (String n : notes) {
                String[] parts = n.split("\\|", 3);
                String buyer = parts[0];
                String item = parts.length > 1 ? parts[1] : "?";
                String price = parts.length > 2 ? parts[2] : "?";
                p.sendMessage(Msg.info("<green>Ton item <white>" + item
                        + "</white> a été vendu pour <yellow>$" + price
                        + "</yellow> à <white>" + buyer + "</white> pendant ton absence.</green>"));
            }
        }, 40L);
    }

    private void applyPending(Player p, PendingTeleportManager.Pending pending) {
        // Run slightly delayed so the world chunk is ready + sync layer has loaded.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            switch (pending.kind()) {
                case LOC -> {
                    World w = resolveStoredWorld(pending.world());
                    if (w == null) {
                        p.sendMessage(Msg.err("Monde <white>" + pending.world() + "</white> introuvable ici."));
                        return;
                    }
                    Location loc = new Location(w, pending.x(), pending.y(), pending.z(),
                            pending.yaw(), pending.pitch());
                    p.teleportAsync(loc);
                    p.sendMessage(Msg.ok("<aqua>Téléporté.</aqua>"));
                }
                case RTP -> {
                    World w = resolveStoredWorld(pending.world());
                    if (w == null) {
                        p.sendMessage(Msg.err("Monde <white>" + pending.world() + "</white> introuvable."));
                        return;
                    }
                    if (plugin.rtp() == null) {
                        p.sendMessage(Msg.err("RTP indisponible."));
                        return;
                    }
                    p.sendMessage(Msg.info("<aqua>Recherche d'un lieu sûr...</aqua>"));
                    plugin.rtp().teleport(p, w);
                }
                case SPAWN -> {
                    Location loc = plugin.spawns().spawn();
                    if (loc == null) loc = plugin.spawns().hub();
                    if (loc != null) {
                        p.teleportAsync(loc);
                        p.sendMessage(Msg.ok("<aqua>Spawn.</aqua>"));
                    }
                }
            }
        }, 10L);
    }

    private World resolveStoredWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World exact = Bukkit.getWorld(worldName);
        if (exact != null) {
            return exact;
        }
        return plugin.resolveWorld(worldName, inferEnvironment(worldName));
    }

    private World.Environment inferEnvironment(String worldName) {
        String lower = worldName.toLowerCase(Locale.ROOT);
        if (lower.contains("nether")) {
            return World.Environment.NETHER;
        }
        if (lower.contains("the_end") || lower.endsWith("_end") || lower.equals("end")) {
            return World.Environment.THE_END;
        }
        return World.Environment.NORMAL;
    }

    private void applyLobbyMode(Player player) {
        if (player.hasPermission("smp.gamemode.keep")) {
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0f);
    }

    private void applyPtrMode(Player player) {
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0f);
    }

    private void applySurvivalMode(Player player) {
        if (player.hasPermission("smp.gamemode.keep")) {
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        event.quitMessage(null);

        if (plugin.combat() != null && plugin.combat().isTagged(p)) {
            // Combat log: kill + drop
            p.setHealth(0);
            plugin.logs().log(LogCategory.COMBAT, p, "combat_log_kill");
        }

        if (plugin.isMainSurvival()) {
            PlayerData d = plugin.players().get(p.getUniqueId());
            Location loc = p.getLocation();
            if (d != null && loc.getWorld() != null) {
                d.setLastLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                        loc.getYaw(), loc.getPitch());
            }
        }

        if (plugin.tpa() != null) plugin.tpa().cancelOutgoing(p.getUniqueId());
        if (plugin.rtp() != null) plugin.rtp().unload(p.getUniqueId());
        if (plugin.combat() != null) plugin.combat().untag(p);
        if (plugin.cooldowns() != null) plugin.cooldowns().unload(p.getUniqueId());
        if (plugin.scoreboard() != null) plugin.scoreboard().remove(p);
        if (plugin.nametags() != null) plugin.nametags().forget(p.getUniqueId());
        if (plugin.playtime() != null) plugin.playtime().syncNow(p);
        if (plugin.permissions() != null) plugin.permissions().detach(p);

        plugin.players().unload(p.getUniqueId());
        plugin.logs().log(LogCategory.JOIN, p, "quit");
    }
}
