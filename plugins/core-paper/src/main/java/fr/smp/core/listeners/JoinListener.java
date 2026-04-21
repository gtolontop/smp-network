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
        PendingTeleportManager.Pending pending = plugin.pendingTp() != null
                ? plugin.pendingTp().peek(p.getUniqueId()) : null;
        boolean pendingFresh = pending != null
                && System.currentTimeMillis() - pending.createdAt() < 60_000;
        if (pendingFresh) {
            plugin.pendingTp().consume(p.getUniqueId());
            applyPending(p, pending);
        } else if (plugin.isLobby()) {
            Location hub = plugin.spawns().hub();
            if (hub != null) p.teleportAsync(hub);
        } else {
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR
                    && !p.hasPermission("smp.gamemode.keep")) {
                p.setGameMode(GameMode.SURVIVAL);
            }
            if (!data.survivalJoined()) {
                String worldName = plugin.getConfig().getString("rtp.default-world", "world");
                World w = Bukkit.getWorld(worldName);
                if (w != null) {
                    p.sendMessage(Msg.info("<aqua>Bienvenue ! Téléportation aléatoire...</aqua>"));
                    plugin.rtp().teleport(p, w);
                } else {
                    Location s = plugin.spawns().spawn();
                    if (s != null) p.teleportAsync(s);
                }
            } else if (data.hasLastLocation()) {
                World lw = Bukkit.getWorld(data.lastWorld());
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

        if (!plugin.isLobby()) data.setSurvivalJoined(true);

        if (plugin.scoreboard() != null) plugin.scoreboard().apply(p);
        if (plugin.tabList() != null) plugin.tabList().update(p);
        if (plugin.nametags() != null) plugin.nametags().refresh(p);
        if (plugin.hunted() != null) plugin.hunted().refreshOnJoin(p);
    }

    private void applyPending(Player p, PendingTeleportManager.Pending pending) {
        // Run slightly delayed so the world chunk is ready + sync layer has loaded.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            switch (pending.kind()) {
                case LOC -> {
                    World w = Bukkit.getWorld(pending.world());
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
                    World w = Bukkit.getWorld(pending.world());
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        event.quitMessage(null);

        if (plugin.combat() != null && plugin.combat().isTagged(p)) {
            // Combat log: kill + drop
            p.setHealth(0);
            plugin.logs().log(LogCategory.COMBAT, p, "combat_log_kill");
        }

        if (!plugin.isLobby()) {
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
        if (plugin.scoreboard() != null) plugin.scoreboard().remove(p);
        if (plugin.nametags() != null) plugin.nametags().forget(p.getUniqueId());
        if (plugin.playtime() != null) plugin.playtime().syncNow(p);
        if (plugin.permissions() != null) plugin.permissions().detach(p);

        plugin.players().unload(p.getUniqueId());
        plugin.logs().log(LogCategory.JOIN, p, "quit");
    }
}
