package fr.smp.core;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class KickHandler {

    private final SMPCoreVelocity plugin;
    private final ProxyServer server;
    private final MiniMessage mm;

    public KickHandler(SMPCoreVelocity plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.mm = plugin.getMiniMessage();
    }

    @Subscribe(order = PostOrder.LATE)
    public void onKicked(KickedFromServerEvent event) {
        String failedServer = event.getServer().getServerInfo().getName();
        String rawReason = event.getServerKickReason()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .orElse("")
                .trim();
        String lower = rawReason.toLowerCase();

        Category cat = classify(lower);

        if (cat == Category.VERSION_MISMATCH) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(mm.deserialize(
                    "<gradient:#ff6b6b:#feca57><bold>Version incompatible</bold></gradient>\n\n"
                    + "<red>✗ Ta version de Minecraft ne correspond pas au serveur.</red>\n\n"
                    + "<gray>Version requise : <white>26.1.2</white></gray>\n"
                    + "<gray>Change ta version dans le launcher, puis reconnecte-toi.</gray>\n\n"
                    + "<dark_gray>smp.network · " + failedServer + "</dark_gray>"
            )));
            return;
        }

        if (cat == Category.BANNED) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(mm.deserialize(
                    "<gradient:#ff6b6b:#ee5a6f><bold>Banni du serveur</bold></gradient>\n\n"
                    + "<red>✗ Tu es banni de <white>" + failedServer + "</white>.</red>\n\n"
                    + "<gray>Raison : <white>" + mm.escapeTags(firstLine(rawReason)) + "</white></gray>\n"
                    + "<gray>Tu peux faire appel sur le Discord.</gray>\n\n"
                    + "<dark_gray>smp.network</dark_gray>"
            )));
            return;
        }

        if (cat == Category.WHITELIST) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(mm.deserialize(
                    "<gradient:#fde68a:#fb923c><bold>Accès restreint</bold></gradient>\n\n"
                    + "<yellow>⚠ <white>" + failedServer + "</white> est en whitelist.</yellow>\n\n"
                    + "<gray>Tu n'es pas autorisé à rejoindre ce serveur pour l'instant.</gray>\n"
                    + "<gray>Demande l'accès sur le Discord si tu penses que c'est une erreur.</gray>\n\n"
                    + "<dark_gray>smp.network</dark_gray>"
            )));
            return;
        }

        if (cat == Category.SERVER_FULL) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(mm.deserialize(
                    "<gradient:#fde68a:#fb923c><bold>Serveur complet</bold></gradient>\n\n"
                    + "<yellow>⚠ <white>" + failedServer + "</white> est plein.</yellow>\n\n"
                    + "<gray>Réessaie dans quelques minutes, un slot va se libérer.</gray>\n\n"
                    + "<dark_gray>smp.network</dark_gray>"
            )));
            return;
        }

        // Cas UNREACHABLE / SHUTDOWN / UNKNOWN → on tente un fallback vers lobby.
        RegisteredServer fallback = pickFallback(failedServer);

        if (fallback != null) {
            String serverTitle = capitalize(failedServer);
            String reason = switch (cat) {
                case UNREACHABLE -> "est injoignable (probablement en cours de démarrage)";
                case SHUTDOWN   -> "redémarre";
                default         -> "a rencontré un problème";
            };
            Component notice = mm.deserialize(
                    "<yellow>⚠ " + serverTitle + " " + reason + ".</yellow>"
                    + " <gray>Retour au lobby...</gray>"
            );
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(fallback, notice));
            return;
        }

        String title = switch (cat) {
            case UNREACHABLE -> "Serveur injoignable";
            case SHUTDOWN   -> "Serveur en redémarrage";
            default         -> "Connexion interrompue";
        };
        String body = switch (cat) {
            case UNREACHABLE -> "<gray>Le serveur <white>" + failedServer + "</white> n'est pas disponible actuellement.</gray>\n"
                             + "<gray>Réessaie dans quelques instants.</gray>";
            case SHUTDOWN   -> "<gray>Le serveur <white>" + failedServer + "</white> redémarre.</gray>\n"
                             + "<gray>Reviens dans 30 secondes.</gray>";
            default         -> "<gray>Tu as été déconnecté de <white>" + failedServer + "</white>.</gray>\n"
                             + "<gray>Raison : <white>" + mm.escapeTags(firstLine(rawReason)) + "</white></gray>";
        };

        Component screen = mm.deserialize(
                "<gradient:#ff6b6b:#feca57><bold>" + title + "</bold></gradient>\n\n"
                + body + "\n\n"
                + "<dark_gray>smp.network</dark_gray>"
        );
        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(screen));
    }

    private enum Category { VERSION_MISMATCH, UNREACHABLE, SHUTDOWN, BANNED, WHITELIST, SERVER_FULL, UNKNOWN }

    private static Category classify(String lower) {
        if (lower.isEmpty()) return Category.UNREACHABLE;

        if (lower.contains("outdated client") || lower.contains("outdated server")
                || lower.contains("incompatible") || lower.contains("unsupported version")
                || lower.contains("multiplayer.disconnect.outdated")
                || lower.contains("protocol version")) {
            return Category.VERSION_MISMATCH;
        }
        if (lower.contains("banned") || lower.contains("banni")
                || lower.contains("you are banned")) {
            return Category.BANNED;
        }
        if (lower.contains("whitelist") || lower.contains("not white-listed")
                || lower.contains("liste blanche")) {
            return Category.WHITELIST;
        }
        if (lower.contains("server is full") || lower.contains("too many players")
                || lower.contains("serveur est plein")) {
            return Category.SERVER_FULL;
        }
        if (lower.contains("connection refused") || lower.contains("connect timed out")
                || lower.contains("read timed out") || lower.contains("connection reset")
                || lower.contains("no route to host") || lower.contains("host is down")
                || lower.contains("unable to connect") || lower.contains("could not connect")
                || lower.contains("io.netty")) {
            return Category.UNREACHABLE;
        }
        if (lower.contains("server closed") || lower.contains("shutting down")
                || lower.contains("restarting") || lower.contains("server stopped")
                || lower.contains("multiplayer.disconnect.server_shutdown")) {
            return Category.SHUTDOWN;
        }
        return Category.UNKNOWN;
    }

    private RegisteredServer pickFallback(String failedName) {
        for (RegisteredServer rs : server.getAllServers()) {
            String name = rs.getServerInfo().getName();
            if (name.equalsIgnoreCase(failedName)) continue;
            if (!name.toLowerCase().startsWith("lobby")) continue;
            if (isOnline(rs)) return rs;
        }
        for (RegisteredServer rs : server.getAllServers()) {
            String name = rs.getServerInfo().getName();
            if (name.equalsIgnoreCase(failedName)) continue;
            if (name.toLowerCase().startsWith("lobby")) continue;
            if (isOnline(rs)) return rs;
        }
        return null;
    }

    private boolean isOnline(RegisteredServer rs) {
        try {
            return rs.ping().get(1, java.util.concurrent.TimeUnit.SECONDS) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String line = nl == -1 ? s : s.substring(0, nl);
        return line.length() > 120 ? line.substring(0, 117) + "..." : line;
    }

}
