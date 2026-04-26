package fr.smp.anticheat.clients;

import fr.smp.anticheat.AntiCheatPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module de détection de clients non-vanilla / trichés côté serveur.
 *
 * <h3>Sources de détection</h3>
 * <ul>
 *   <li><b>Brand</b> — {@code minecraft:brand} arrive peu après le join. On lit
 *       {@link Player#getClientBrandName()} avec un délai de 2s puis on matche contre
 *       les patterns connus dans {@link CheatSignatures}.</li>
 *   <li><b>Plugin channels</b> — {@link PlayerRegisterChannelEvent} se déclenche
 *       quand le client enregistre un channel de plugin-messaging. Les clients
 *       trichés signent souvent leurs channels même quand ils spoofent le brand.</li>
 *   <li><b>Freecam heuristique</b> — délégué à {@link FreecamDetector} via
 *       l'event {@link org.bukkit.event.player.PlayerMoveEvent}.</li>
 * </ul>
 *
 * <h3>Action sur détection</h3>
 * <ul>
 *   <li><b>Lobby / CHEAT</b>: kick immédiat avec message générique, plus coupure
 *       du chat dès qu'un flag CHEAT existe pour fermer la fenêtre de course.</li>
 *   <li><b>Lobby / GREY</b>: flag stocké dans un metadata {@code smp_ac_block_transfer}
 *       qui bloque le transfert vers survie (lu par {@code SMPCore.MessageChannel}).</li>
 *   <li><b>Survie</b>: kick immédiat (inverse de la protection voulue — un cheat
 *       qui arrive en survie malgré le gate est déjà un problème).</li>
 * </ul>
 */
public final class ClientDetectionModule implements Listener {

    public static final String BLOCK_TRANSFER_METADATA = "smp_ac_block_transfer";

    private final AntiCheatPlugin plugin;
    private final boolean enabled;
    private final boolean blockTransferOnLobby;
    private final boolean kickOnLobbyCheat;
    private final boolean kickOnSurvival;
    private final boolean flagGreyChannels;
    private final boolean silenceCheatChat;
    private final String genericKickMessage;
    private final boolean freecamEnabled;
    private final FreecamDetector freecam;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Map<UUID, ClientProfile> profiles = new ConcurrentHashMap<>();

    public ClientDetectionModule(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        var c = plugin.getConfig().getConfigurationSection("clients");
        this.enabled = c == null || c.getBoolean("enabled", true);
        this.blockTransferOnLobby = c == null || c.getBoolean("block-transfer-to-survival", true);
        this.kickOnLobbyCheat = c == null || c.getBoolean("kick-on-lobby-cheat", true);
        this.kickOnSurvival = c != null && c.getBoolean("kick-on-survival", true);
        this.flagGreyChannels = c == null || c.getBoolean("flag-grey-channels", true);
        this.silenceCheatChat = c == null || c.getBoolean("silence-cheat-chat", true);
        this.genericKickMessage = c != null
                ? c.getString("generic-kick-message", "<gray>Connexion refusée.</gray>")
                : "<gray>Connexion refusée.</gray>";
        this.freecamEnabled = c == null || c.getBoolean("freecam.enabled", true);
        int freecamThreshold = c != null ? c.getInt("freecam.threshold-ticks", 160) : 160;
        this.freecam = new FreecamDetector(plugin, this, freecamThreshold);
    }

    public boolean enabled() { return enabled; }
    public FreecamDetector freecam() { return freecam; }

    public ClientProfile get(UUID id) { return profiles.get(id); }
    public Map<UUID, ClientProfile> allProfiles() { return Collections.unmodifiableMap(profiles); }

    public ClientProfile getOrCreate(Player p) {
        return profiles.computeIfAbsent(p.getUniqueId(), id -> new ClientProfile());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        Player p = e.getPlayer();
        ClientProfile profile = getOrCreate(p);
        // Premier probe rapide pour attraper les brands disponibles quasi immédiatement,
        // puis fallback plus tard avec snapshot des channels déjà enregistrés.
        scheduleInspection(p, profile, 8L, false);
        scheduleInspection(p, profile, 40L, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        profiles.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!enabled || !silenceCheatChat) return;
        if (!hasCheatFlag(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegister(PlayerRegisterChannelEvent e) {
        if (!enabled) return;
        recordChannel(e.getPlayer(), getOrCreate(e.getPlayer()), e.getChannel());
    }

    private void scheduleInspection(Player p, ClientProfile profile, long delayTicks, boolean includeChannelSnapshot) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> inspectClient(p, profile, includeChannelSnapshot), delayTicks);
    }

    private void inspectClient(Player p, ClientProfile profile, boolean includeChannelSnapshot) {
        if (!p.isOnline()) return;

        String brand = safeBrand(p);
        if (!"?".equals(brand) && !brand.equals(profile.brand())) {
            profile.setBrand(brand);
            CheatSignatures.MatchResult m = CheatSignatures.matchBrand(brand);
            if (m != null) {
                raiseFlag(p, profile, m.severity(), "brand", brand + " ~" + m.needle());
            }
        }

        if (includeChannelSnapshot) {
            // Fallback: enregistrer les channels déjà souscrits (au cas où REGISTER
            // arrive AVANT notre listener et on les raterait).
            for (String ch : p.getListeningPluginChannels()) {
                recordChannel(p, profile, ch);
            }
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[AC/clients] " + p.getName() + " brand=" + profile.brand()
                        + " channels=" + p.getListeningPluginChannels().size()
                        + " flags=" + profile.flags());
            }
        }
    }

    /** Enregistre un channel et déclenche un flag si c'est une signature connue. */
    public void recordChannel(Player p, ClientProfile profile, String channel) {
        profile.channels().add(channel);
        CheatSignatures.MatchResult m = CheatSignatures.matchChannel(channel);
        if (m == null) return;
        if (m.severity() == CheatSignatures.Severity.GREY && !flagGreyChannels) return;
        raiseFlag(p, profile, m.severity(), "channel", channel + " ~" + m.needle());
    }

    /** Ajoute un flag et applique l'action configurée (block-transfer / kick). */
    public void raiseFlag(Player p, ClientProfile profile,
                          CheatSignatures.Severity severity, String source, String detail) {
        ClientProfile.Flag flag = new ClientProfile.Flag(severity, source, detail);
        boolean firstForThisSource = profile.flags().stream().noneMatch(
                f -> f.source().equals(source) && f.detail().equals(detail));
        profile.addFlag(flag);
        if (!firstForThisSource) return;

        plugin.getLogger().warning("[AC/clients] " + p.getName() + " flagged "
                + severity.name() + " via " + source + ": " + detail);

        applyAction(p, profile, severity, source, detail);
    }

    private void applyAction(Player p, ClientProfile profile,
                             CheatSignatures.Severity severity, String source, String detail) {
        String serverType = resolveServerType();
        String encodedReason = severity.name().toLowerCase() + ":" + source + ":" + detail;

        if ("lobby".equalsIgnoreCase(serverType)) {
            if (severity == CheatSignatures.Severity.CHEAT && kickOnLobbyCheat) {
                p.setMetadata(BLOCK_TRANSFER_METADATA, new FixedMetadataValue(plugin, encodedReason));
                p.kick(mm.deserialize(genericKickMessage));
                return;
            }
            if (blockTransferOnLobby) {
                // Pose un metadata lu par MessageChannel#sendTransfer côté SMPCore
                // pour refuser le transfert vers survie. On reste volontairement
                // discret: pas de message explicite au joueur pour éviter de lui
                // confirmer quelle signature a match.
                p.setMetadata(BLOCK_TRANSFER_METADATA, new FixedMetadataValue(plugin, encodedReason));
            }
            return;
        }
        if (!"lobby".equalsIgnoreCase(serverType) && kickOnSurvival
                && severity == CheatSignatures.Severity.CHEAT) {
            p.kick(mm.deserialize(genericKickMessage));
        }
    }

    public boolean isBlocked(Player p) {
        return p.hasMetadata(BLOCK_TRANSFER_METADATA);
    }

    public boolean hasCheatFlag(Player p) {
        ClientProfile profile = profiles.get(p.getUniqueId());
        return profile != null && profile.worstSeverity() == CheatSignatures.Severity.CHEAT;
    }

    public void clearBlock(Player p) {
        p.removeMetadata(BLOCK_TRANSFER_METADATA, plugin);
        ClientProfile profile = profiles.get(p.getUniqueId());
        if (profile != null) profile.clearFlags();
    }

    private String safeBrand(Player p) {
        try {
            String b = p.getClientBrandName();
            return b == null ? "?" : b;
        } catch (Throwable t) {
            return "?";
        }
    }

    /**
     * Même logique que SMPCore.resolveServerType — JVM property > env > working dir.
     * On ne peut pas dépendre de la classe SMPCore (soft dep), donc on duplique.
     */
    public static String resolveServerType() {
        String prop = System.getProperty("smp.server.type");
        if (prop != null && !prop.isBlank()) return prop.toLowerCase();
        String env = System.getenv("SMP_SERVER_TYPE");
        if (env != null && !env.isBlank()) return env.toLowerCase();
        String cwd = new java.io.File("").getAbsolutePath().replace('\\', '/');
        String lower = cwd.toLowerCase();
        if (lower.endsWith("/survival") || lower.contains("/survival/")) return "survival";
        if (lower.endsWith("/lobby") || lower.contains("/lobby/")) return "lobby";
        return "unknown";
    }
}
