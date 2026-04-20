package fr.smp.core.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snapshot of all players online across the network, fed by the proxy every
 * few seconds. Used by the tab list (and anything else that wants a global
 * player view without doing another round-trip).
 */
public class NetworkRoster {

    public record Entry(String name, String server, String prefix) {}

    private final Map<String, Entry> byName = new ConcurrentHashMap<>();

    public void replace(List<Entry> entries) {
        byName.clear();
        for (Entry e : entries) byName.put(e.name().toLowerCase(), e);
    }

    public List<Entry> all() {
        List<Entry> out = new ArrayList<>(byName.values());
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    public List<Entry> onServer(String server) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byName.values()) if (server.equalsIgnoreCase(e.server())) out.add(e);
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    public int total() { return byName.size(); }

    public Entry get(String name) {
        if (name == null) return null;
        return byName.get(name.toLowerCase());
    }

    public List<Entry> immutable() {
        return Collections.unmodifiableList(all());
    }
}
