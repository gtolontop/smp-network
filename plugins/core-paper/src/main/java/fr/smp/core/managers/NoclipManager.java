package fr.smp.core.managers;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoclipManager implements Listener {

    private final Set<UUID> noclipPlayers = ConcurrentHashMap.newKeySet();

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        boolean enable = !noclipPlayers.contains(uuid);
        applyNoclip(player, enable);
        if (enable) {
            noclipPlayers.add(uuid);
            player.setAllowFlight(true);
            player.setFlying(true);
        } else {
            noclipPlayers.remove(uuid);
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                    && !player.hasPermission("smp.admin")) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        return enable;
    }

    public boolean isNoclip(Player player) {
        return noclipPlayers.contains(player.getUniqueId());
    }

    public void disable(Player player) {
        if (noclipPlayers.remove(player.getUniqueId())) {
            applyNoclip(player, false);
        }
    }

    public Set<UUID> getNoclipPlayers() {
        return Collections.unmodifiableSet(noclipPlayers);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        disable(e.getPlayer());
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        if (!noclipPlayers.contains(e.getPlayer().getUniqueId())) return;
        if (!e.isFlying()) {
            e.setCancelled(true);
        }
    }

    private static void applyNoclip(Player player, boolean enable) {
        ServerPlayer sp = ((CraftPlayer) player).getHandle();
        sp.noPhysics = enable;
    }
}
