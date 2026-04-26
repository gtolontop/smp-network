package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.auth.AuthSession;
import fr.smp.core.managers.ModerationManager;
import fr.smp.core.utils.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Détection anti-flood + anti-pub. Cancel le message, auto-mute via ModerationManager,
 * alerte les admins. Tourne à LOWEST pour courir avant ChatListener (NORMAL)
 * et le check mute de ModerationManager (HIGHEST).
 */
public class SpamGuard implements Listener {

    private static final class Tracker {
        final Deque<Long> timestamps = new ArrayDeque<>();
        String lastMessage = "";
        int duplicateCount = 0;
        long lastWarmupWarnAt = 0L;
    }

    // Noms de clients de triche connus (en clair, après décodage leet).
    private static final String[] CHEAT_CLIENTS = {
        "liquidbounce", "wurst", "meteor", "impact", "nodus", "sigma", "rise",
        "astolfo", "rusherhack", "novoline", "wolfram", "inertia", "aristois",
        "nextgen", "ccblue", "ccb1uex", "ccb", "liqr", "future", "substrate",
        "optifine.net", "ghostclient", "aimbot", "esp hack", "xray hack"
    };
    private static final Pattern INTERPOLATION_PAYLOAD = Pattern.compile(
            "(?i)(?:\\$\\{|#\\{|%\\{)[^\\r\\n}]{0,160}(?:jndi|ldap|ldaps|rmi|dns|iiop|nis|nds|corba|env|sys|lower|upper|date|main|::-)[^\\r\\n}]{0,160}");

    private final SMPCore plugin;
    private final ConcurrentHashMap<UUID, Tracker> trackers = new ConcurrentHashMap<>();

    public SpamGuard(SMPCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Décode le leet speak. oneAsL=true mappe '1'→'l' (pour liquidbounce etc.),
     * false mappe '1'→'i'. Les deux passes sont nécessaires car '1' sert les deux.
     */
    private static String decodeLeet(String lower, boolean oneAsL) {
        char[] buf = lower.toCharArray();
        for (int i = 0; i < buf.length; i++) {
            buf[i] = switch (buf[i]) {
                case '0' -> 'o';
                case '1' -> oneAsL ? 'l' : 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '6' -> 'g';
                case '7' -> 't';
                case '@' -> 'a';
                case '$' -> 's';
                case '!' -> 'i';
                default  -> buf[i];
            };
        }
        return new String(buf);
    }

    private static boolean containsClient(String decoded) {
        for (String client : CHEAT_CLIENTS) {
            if (decoded.contains(client)) return true;
        }
        return false;
    }

    private static boolean containsUrl(String s) {
        return s.contains(".net") || s.contains(".com") || s.contains(".gg")
                || s.contains(".org") || s.contains("://");
    }

    private static String normalizeWhitespace(String text) {
        return text.replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ')
                .trim();
    }

    private static boolean isExploitPayload(String text) {
        String lower = text.toLowerCase();
        if (INTERPOLATION_PAYLOAD.matcher(lower).find()) return true;
        return (lower.contains("${") || lower.contains("#{") || lower.contains("%{"))
                && (lower.contains("jndi:") || lower.contains("ldap://") || lower.contains("ldaps://")
                || lower.contains("rmi://") || lower.contains("dns://")
                || lower.contains("iiop://") || lower.contains("nis://")
                || lower.contains("nds://") || lower.contains("corba://"));
    }

    private long connectionAgeMs(Player player) {
        if (plugin.auth() == null) return Long.MAX_VALUE;
        AuthSession session = plugin.auth().session(player.getUniqueId());
        if (session == null) return Long.MAX_VALUE;
        return System.currentTimeMillis() - session.joinedAt();
    }

    private static boolean shouldWarnWarmup(Tracker tracker, long now) {
        if (now - tracker.lastWarmupWarnAt < 1000L) return false;
        tracker.lastWarmupWarnAt = now;
        return true;
    }

    /**
     * Retourne true si le message ressemble à une pub de cheat client.
     * On tente les deux décodages (1→l et 1→i) et on vérifie les URL
     * aussi sur le texte décodé (ex. ".n3t" → ".net").
     */
    private boolean isAdvertSpam(String text) {
        String lower = text.toLowerCase();
        String decodedL = decodeLeet(lower, true);   // 1 = l  (liquidbounce, ...)
        String decodedI = decodeLeet(lower, false);  // 1 = i  (rare mais possible)

        boolean hasUrl = containsUrl(lower) || containsUrl(decodedL) || containsUrl(decodedI);
        boolean hasClient = containsClient(decodedL) || containsClient(decodedI)
                || containsClient(lower); // sans décodage, au cas où

        if (!hasUrl && !hasClient) return false;

        // Ratio de caractères leet sur les positions alphanumériques
        int leetCount = 0, alphaCount = 0;
        for (char c : lower.toCharArray()) {
            if (Character.isLetterOrDigit(c)) alphaCount++;
            if ("01345678@$!".indexOf(c) >= 0) leetCount++;
        }
        double leetRatio = alphaCount > 5 ? (double) leetCount / alphaCount : 0;

        // Seuil à 10 % : les vraies pubs ont 30-50 % de leet
        return leetRatio > 0.10;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.antispam.enabled", true)) return;
        Player p = event.getPlayer();
        if (p.hasPermission("smp.chat.antispam.bypass")) return;
        if (plugin.moderation() != null && plugin.moderation().activeMute(p.getUniqueId()) != null) return;

        String text = normalizeWhitespace(PlainTextComponentSerializer.plainText().serialize(event.message()));
        if (text.isEmpty()) return;

        Tracker t = trackers.computeIfAbsent(p.getUniqueId(), k -> new Tracker());

        if (isExploitPayload(text)) {
            event.setCancelled(true);
            String payloadDuration = plugin.getConfig().getString("chat.antispam.payload-mute-duration", "1d");
            long payloadSec = ModerationManager.parseDuration(payloadDuration);
            if (payloadSec <= 0) payloadSec = 86400L;
            final long finalSec = payloadSec;
            Bukkit.getScheduler().runTask(plugin,
                    () -> applyMuteAndKick(p, "Payload interdit en chat", finalSec, text,
                            plugin.getConfig().getString("chat.antispam.payload-kick-message",
                                    "<red>Connexion refusée.</red>")));
            return;
        }

        // Détection publicité avant le rate-limit — kick + mute (pas que mute : un
        // bot remis au mute continue de consommer une slot, on le vire.)
        if (isAdvertSpam(text)) {
            event.setCancelled(true);
            String advertDuration = plugin.getConfig().getString("chat.antispam.advert-mute-duration", "1h");
            long advertSec = ModerationManager.parseDuration(advertDuration);
            if (advertSec <= 0) advertSec = 3600L;
            final long finalSec = advertSec;
            Bukkit.getScheduler().runTask(plugin,
                    () -> applyMuteAndKick(p, "Publicité interdite (cheat client)", finalSec, text,
                            plugin.getConfig().getString("chat.antispam.advert-kick-message",
                                    "<red>Pub interdite.</red>")));
            return;
        }

        // Gate "compte neuf" : défense SOURCE contre les bots. Un bot s'inscrit et
        // chatte en <1 s sans jamais bouger ni casser un bloc. On exige de chaque
        // compte cracked récemment inscrit : ancienneté minimale ET interactions
        // minimales avant que son chat ne soit visible. Cela tue la vague de bots
        // quelle que soit l'IP/tunnel.
        if (plugin.auth() != null) {
            AuthSession s = plugin.auth().session(p.getUniqueId());
            if (s != null && !s.premium() && s.accountRegisteredAt() > 0) {
                long now2 = System.currentTimeMillis();
                long accountAgeMs = now2 - s.accountRegisteredAt();
                long minAgeMs = plugin.getConfig().getLong("chat.antispam.new-account.min-age-ms", 120_000);
                int minInteractions = plugin.getConfig().getInt("chat.antispam.new-account.min-interactions", 5);
                if (accountAgeMs < minAgeMs || s.interactions() < minInteractions) {
                    event.setCancelled(true);
                    long remainSec = Math.max(1, (minAgeMs - accountAgeMs) / 1000);
                    int remainInter = Math.max(0, minInteractions - s.interactions());
                    p.sendActionBar(Msg.mm(
                            "<red>Anti-bot :</red> <gray>bouge / clique un peu puis réessaie (" +
                            remainInter + " interactions, " + remainSec + "s).</gray>"));
                    return;
                }
            }
        }

        int msgThreshold = plugin.getConfig().getInt("chat.antispam.rate-limit.messages", 3);
        long windowMs = plugin.getConfig().getLong("chat.antispam.rate-limit.window-seconds", 3) * 1000L;
        int dupThreshold = plugin.getConfig().getInt("chat.antispam.duplicate.threshold", 2);
        boolean rateViolation;
        boolean dupViolation;
        int currentRate;
        int currentDup;
        long now = System.currentTimeMillis();

        synchronized (t) {
            while (!t.timestamps.isEmpty() && now - t.timestamps.peekFirst() > windowMs) {
                t.timestamps.pollFirst();
            }
            t.timestamps.addLast(now);

            String norm = text.toLowerCase();
            if (norm.equals(t.lastMessage)) {
                t.duplicateCount++;
            } else {
                t.duplicateCount = 1;
                t.lastMessage = norm;
            }

            currentRate = t.timestamps.size();
            currentDup = t.duplicateCount;
            rateViolation = currentRate > msgThreshold;
            dupViolation = currentDup > dupThreshold;

            if (rateViolation || dupViolation) {
                t.timestamps.clear();
                t.duplicateCount = 0;
            }
        }

        if (plugin.getConfig().getBoolean("chat.antispam.fresh-join.enabled", true)) {
            long joinAgeMs = connectionAgeMs(p);
            long blockMs = plugin.getConfig().getLong("chat.antispam.fresh-join.block-seconds", 6) * 1000L;
            if (joinAgeMs < blockMs) {
                event.setCancelled(true);

                int warmupAttempts = plugin.getConfig().getInt("chat.antispam.fresh-join.max-attempts", 2);
                if (currentRate > warmupAttempts || currentDup > 1) {
                    String warmupDuration = plugin.getConfig().getString("chat.antispam.fresh-join.mute-duration", "30m");
                    long warmupSec = ModerationManager.parseDuration(warmupDuration);
                    if (warmupSec <= 0) warmupSec = 1800L;
                    final long finalSec = warmupSec;
                    Bukkit.getScheduler().runTask(plugin,
                            () -> applyMuteAndKick(p, "Spam à chaud juste après connexion", finalSec, text,
                                    plugin.getConfig().getString("chat.antispam.fresh-join.kick-message",
                                            "<red>Connexion refusée.</red>")));
                    return;
                }

                if (shouldWarnWarmup(t, now)) {
                    long remaining = Math.max(1L, (blockMs - joinAgeMs + 999L) / 1000L);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            p.sendActionBar(Msg.mm("<yellow>Chat débloqué dans <white>" + remaining + "s</white>.</yellow>")));
                }
                return;
            }
        }

        if (!rateViolation && !dupViolation) return;

        event.setCancelled(true);

        String reason = rateViolation
                ? "Flood chat (" + currentRate + " msg en " + (windowMs / 1000) + "s)"
                : "Messages répétés (x" + currentDup + ")";

        String durationSpec = plugin.getConfig().getString("chat.antispam.mute-duration", "10m");
        long parsed = ModerationManager.parseDuration(durationSpec);
        long durationSec = parsed > 0 ? parsed : 600L;

        Bukkit.getScheduler().runTask(plugin, () -> applyMute(p, reason, durationSec, text));
    }

    private void applyMute(Player p, String reason, long durationSec, String lastMessage) {
        if (!p.isOnline()) {
            // toujours appliquer le mute: le record SQLite persiste cross-serveur
        }

        plugin.moderation().mute(p.getUniqueId(), p.getName(), "AUTO-SPAMGUARD", reason, durationSec);

        Component playerMsg = Msg.info("<red><bold>Mute automatique</bold></red> <gray>pour spam — " +
                reason + ". <white>Durée: " + Msg.duration(durationSec) + "</white></gray>");
        if (p.isOnline()) p.sendMessage(playerMsg);

        String sample = lastMessage.length() > 60 ? lastMessage.substring(0, 60) + "…" : lastMessage;
        // Escape MiniMessage tags in the sample so player input can't inject formatting.
        sample = sample.replace("<", "‹").replace(">", "›");

        Component alert = Msg.info("<red><bold>[SpamGuard]</bold></red> <white>" + p.getName() +
                "</white> <gray>auto-mute <white>" + Msg.duration(durationSec) +
                "</white> — <yellow>" + reason + "</yellow></gray>\n" +
                "<dark_gray>» <gray>" + sample + "</gray>");

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("smp.moderation") && !admin.getUniqueId().equals(p.getUniqueId())) {
                admin.sendMessage(alert);
            }
        }

        plugin.getLogger().info("[SpamGuard] " + p.getName() + " auto-muted " +
                durationSec + "s — " + reason);
    }

    private void applyMuteAndKick(Player p, String reason, long durationSec, String lastMessage, String kickMessage) {
        applyMute(p, reason, durationSec, lastMessage);
        plugin.moderation().recordKick(p.getUniqueId(), p.getName(), "AUTO-SPAMGUARD", reason);
        if (p.isOnline()) {
            p.kick(Msg.mm(kickMessage));
        }
    }

    // -------------------------------------------------------- proof-of-human

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        bumpInteraction(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { bumpInteraction(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { bumpInteraction(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) { bumpInteraction(event.getPlayer()); }

    private void bumpInteraction(org.bukkit.entity.Player p) {
        if (plugin.auth() == null) return;
        AuthSession s = plugin.auth().session(p.getUniqueId());
        if (s != null && s.isAuthenticated()) s.incrementInteractions();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        trackers.remove(event.getPlayer().getUniqueId());
    }
}
