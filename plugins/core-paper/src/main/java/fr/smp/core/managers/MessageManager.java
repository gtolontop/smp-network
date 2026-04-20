package fr.smp.core.managers;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks the last private-message partner for each player so {@code /r}
 * replies go to the right person. Kept in-memory because the use-case
 * is short-lived and a restart cleanly drops the stale pointer.
 */
public class MessageManager {

    private final ConcurrentMap<UUID, UUID> lastTalker = new ConcurrentHashMap<>();

    public void remember(UUID from, UUID to) {
        lastTalker.put(from, to);
        lastTalker.put(to, from);
    }

    public UUID last(UUID of) {
        return lastTalker.get(of);
    }

    public void forget(UUID of) {
        lastTalker.remove(of);
    }
}
