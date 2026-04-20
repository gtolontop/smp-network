package fr.smp.core;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

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

        RegisteredServer fallback = pickFallback(failedServer);

        if (fallback != null) {
            Component notice = mm.deserialize(
                "<yellow>⚠ Le serveur <white>" + failedServer + "</white> est indisponible."
                + " Redirection vers <white>" + fallback.getServerInfo().getName() + "</white>...</yellow>"
            );
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(fallback, notice));
            return;
        }

        Component screen = mm.deserialize(
            "<gradient:#ff6b6b:#feca57><bold>SMP Network</bold></gradient>\n\n"
            + "<red>⚠ Serveur indisponible</red>\n\n"
            + "<gray>Le serveur <white>" + failedServer + "</white> est actuellement hors ligne.</gray>\n"
            + "<gray>Aucun autre serveur n'est disponible pour le moment.</gray>\n\n"
            + "<dark_gray>smp.network</dark_gray>"
        );
        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(screen));
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
}
