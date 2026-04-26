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
                VirtualEntryState state = buildState(server, entry, virtual);
                TabListEntry existing = viewer.getTabList().getEntry(virtual).orElse(null);
                if (existing != null) {
                    if (!sameProfile(existing.getProfile(), state.profile())) {
                        viewer.getTabList().removeEntry(virtual);
                    } else {
                        applyState(existing, state);
                        continue;
                    }
                }
                TabListEntry tle = TabListEntry.builder()
                        .tabList(viewer.getTabList())
                        .profile(state.profile())
                        .displayName(state.displayName())
                        .gameMode(state.gameMode())
                        .latency(state.latency())
                        .listed(state.listed())
                        .listOrder(state.listOrder())
                        .showHat(state.showHat())
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

    private static VirtualEntryState buildState(ProxyServer server,
                                                PluginMessageListener.RosterEntry entry,
                                                UUID virtualId) {
        Component fallbackDisplay = MM.deserialize(
                nullToEmpty(entry.prefix()) + "<white>" + entry.name() + "</white>");

        GameProfile profile = new GameProfile(virtualId, entry.name(), java.util.List.of());
        Component displayName = fallbackDisplay;
        int latency = 0;
        int gameMode = 0;
        boolean listed = true;
        int listOrder = 0;
        boolean showHat = true;

        Player subject = server.getPlayer(entry.name()).orElse(null);
        if (subject == null) {
            return new VirtualEntryState(profile, displayName, latency, gameMode, listed, listOrder, showHat);
        }

        TabListEntry selfEntry = subject.getTabList().getEntry(subject.getUniqueId()).orElse(null);
        if (selfEntry != null) {
            profile = selfEntry.getProfile().withId(virtualId);
            displayName = selfEntry.getDisplayNameComponent().orElse(fallbackDisplay);
            latency = selfEntry.getLatency();
            gameMode = selfEntry.getGameMode();
            listed = selfEntry.isListed();
            listOrder = selfEntry.getListOrder();
            showHat = selfEntry.isShowHat();
            return new VirtualEntryState(profile, displayName, latency, gameMode, listed, listOrder, showHat);
        }

        profile = subject.getGameProfile().withId(virtualId);
        latency = safeLatency(subject.getPing());
        if (subject.hasSentPlayerSettings()) {
            showHat = subject.getPlayerSettings().getSkinParts().hasHat();
        }

        return new VirtualEntryState(profile, displayName, latency, gameMode, listed, listOrder, showHat);
    }

    private static void applyState(TabListEntry entry, VirtualEntryState state) {
        entry.setDisplayName(state.displayName());
        entry.setLatency(state.latency());
        entry.setGameMode(state.gameMode());
        entry.setListed(state.listed());
        entry.setListOrder(state.listOrder());
        entry.setShowHat(state.showHat());
    }

    private static boolean sameProfile(GameProfile first, GameProfile second) {
        if (!first.getId().equals(second.getId())) return false;
        if (!first.getName().equals(second.getName())) return false;
        if (first.getProperties().size() != second.getProperties().size()) return false;
        for (int i = 0; i < first.getProperties().size(); i++) {
            GameProfile.Property left = first.getProperties().get(i);
            GameProfile.Property right = second.getProperties().get(i);
            if (!left.getName().equals(right.getName())) return false;
            if (!left.getValue().equals(right.getValue())) return false;
            if (!left.getSignature().equals(right.getSignature())) return false;
        }
        return true;
    }

    private static int safeLatency(long ping) {
        if (ping <= 0L) return 0;
        return ping >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ping;
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

    private record VirtualEntryState(GameProfile profile, Component displayName, int latency,
                                     int gameMode, boolean listed, int listOrder, boolean showHat) {}

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
