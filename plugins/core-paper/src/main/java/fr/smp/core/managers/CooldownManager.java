package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final SMPCore plugin;
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    public CooldownManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    private Map<UUID, Long> getMap(String key) {
        return cooldowns.computeIfAbsent(key, k -> new HashMap<>());
    }

    public long remaining(Player p, String key) {
        if (p.hasPermission("smp.cooldown.bypass." + key) || p.hasPermission("smp.cooldown.bypass.*")) return 0;
        Long until = getMap(key).get(p.getUniqueId());
        if (until == null) return 0;
        long left = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0, left);
    }

    public boolean isOnCooldown(Player p, String key) {
        return remaining(p, key) > 0;
    }

    public void set(Player p, String key, long seconds) {
        getMap(key).put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public void set(Player p, String key) {
        long secs = plugin.getConfig().getLong("cooldowns." + key, getDefaultSeconds(key));
        set(p, key, secs);
    }

    public void unload(UUID uuid) {
        for (Map<UUID, Long> map : cooldowns.values()) {
            map.remove(uuid);
        }
    }

    private long getDefaultSeconds(String key) {
        return switch (key) {
            case "home" -> 10;
            case "tpa" -> 10;
            case "tpaccept" -> 10;
            case "pay" -> 5;
            default -> 10;
        };
    }
}
