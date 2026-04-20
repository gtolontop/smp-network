package fr.smp.core;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps every proxied player's TabList in sync with the full network roster.
 *
 * Why proxy-side: backend servers only know about their own players, so the
 * tab list a Paper client sees normally shows just the backend it is currently
 * on. The proxy sees everyone, so we add "virtual" TabListEntry values for
 * players that live on other backends. Virtual entries are identified by a
 * deterministic v3 UUID derived from the player's name, so across ticks we
 * can recognise and replace them without ghosts.
 */
public final class NetworkTabListSync {

    private static final UUID NAMESPACE = UUID.fromString("7f000000-0000-4000-8000-00000000aabb");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private NetworkTabListSync() {}

    public static void syncAll(ProxyServer server, Collection<Player> viewers,
                               Collection<PluginMessageListener.RosterEntry> roster) {
        for (Player viewer : viewers) {
            String viewerServer = viewer.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("");
            Set<UUID> want = new HashSet<>();

            for (PluginMessageListener.RosterEntry entry : roster) {
                if (entry == null || entry.name() == null) continue;
                // Skip entries for the viewer's own backend — the proxy already
                // adds real entries for those players.
                if (entry.server() != null && entry.server().equalsIgnoreCase(viewerServer)) continue;
                if (entry.name().equalsIgnoreCase(viewer.getUsername())) continue;

                UUID virtual = virtualUuid(entry.name());
                want.add(virtual);
                TabListEntry existing = viewer.getTabList().getEntry(virtual).orElse(null);
                Component display = MM.deserialize(
                        nullToEmpty(entry.prefix()) + "<white>" + entry.name() + "</white>");
                if (existing != null) {
                    existing.setDisplayName(display);
                    continue;
                }
                GameProfile profile = new GameProfile(virtual, entry.name(), java.util.List.of());
                TabListEntry tle = TabListEntry.builder()
                        .tabList(viewer.getTabList())
                        .profile(profile)
                        .displayName(display)
                        .gameMode(0)
                        .latency(0)
                        .listed(true)
                        .build();
                viewer.getTabList().addEntry(tle);
            }

            // Strip stale virtual entries we added last tick.
            for (TabListEntry e : java.util.List.copyOf(viewer.getTabList().getEntries())) {
                UUID id = e.getProfile().getId();
                if (!isVirtual(id)) continue;
                if (!want.contains(id)) viewer.getTabList().removeEntry(id);
            }
        }
    }

    private static UUID virtualUuid(String name) {
        // Java's UUID.nameUUIDFromBytes is MD5-based (v3). Same input → same
        // UUID, which is what we need so we can recognise our own entries
        // and update/remove them without ghosting.
        byte[] src = ("smp-virtual:" + name.toLowerCase()).getBytes(StandardCharsets.UTF_8);
        UUID raw = UUID.nameUUIDFromBytes(src);
        // Fingerprint the UUID with our namespace most-significant bits so we
        // can identify virtual entries later without a side map.
        long msb = (raw.getMostSignificantBits() & 0x0000FFFFFFFFFFFFL) | (NAMESPACE.getMostSignificantBits() & 0xFFFF000000000000L);
        return new UUID(msb, raw.getLeastSignificantBits());
    }

    private static boolean isVirtual(UUID id) {
        return (id.getMostSignificantBits() & 0xFFFF000000000000L)
                == (NAMESPACE.getMostSignificantBits() & 0xFFFF000000000000L);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
