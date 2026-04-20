package fr.smp.anticheat;

import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for "is this player exempt from AntiCheat".
 * Two inputs merge into one answer:
 *   - the permission {@code anticheat.bypass} (durable, managed by LuckPerms / op-bypass.yml)
 *   - a runtime toggle set, flipped via {@code /ac bypass} for testing
 *
 * The runtime set is in-memory only — restarts drop it, which is desirable
 * (test toggles should not survive a restart).
 */
public final class BypassManager {

    private final Set<UUID> runtime = ConcurrentHashMap.newKeySet();

    public boolean isBypassed(Permissible p) {
        if (p instanceof Player pl && runtime.contains(pl.getUniqueId())) return true;
        return p.hasPermission("anticheat.bypass");
    }

    public boolean isRuntimeBypass(UUID id) {
        return runtime.contains(id);
    }

    /** Toggle runtime bypass for the given player. Returns the new state. */
    public boolean toggle(UUID id) {
        if (runtime.contains(id)) {
            runtime.remove(id);
            return false;
        }
        runtime.add(id);
        return true;
    }

    public void setRuntime(UUID id, boolean on) {
        if (on) runtime.add(id);
        else runtime.remove(id);
    }

    public void clear(UUID id) {
        runtime.remove(id);
    }
}
