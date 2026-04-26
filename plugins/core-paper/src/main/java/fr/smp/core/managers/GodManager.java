package fr.smp.core.managers;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GodManager {

    private final Set<UUID> godPlayers = ConcurrentHashMap.newKeySet();

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (godPlayers.contains(uuid)) {
            godPlayers.remove(uuid);
            return false;
        }
        godPlayers.add(uuid);
        return true;
    }

    public boolean isGod(Player player) {
        return godPlayers.contains(player.getUniqueId());
    }

    public void remove(Player player) {
        godPlayers.remove(player.getUniqueId());
    }

    public Set<UUID> getGodPlayers() {
        return Collections.unmodifiableSet(godPlayers);
    }
}
