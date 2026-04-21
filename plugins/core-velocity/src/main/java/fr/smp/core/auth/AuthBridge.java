package fr.smp.core.auth;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

/**
 * Forces online-mode authentication for any connecting username that Mojang
 * reports as a registered premium account.
 *
 * The proxy is configured with online-mode = false (the network accepts
 * cracked clients), but if a cracked client tries to log in with a name that
 * belongs to a real Mojang account, Velocity will demand a Mojang session
 * during handshake and the cracked client will fail with "Bad Login" /
 * "Authentication failed". This is the only way to prevent username
 * impersonation on a cracked-friendly network.
 *
 * Names that don't exist on Mojang's side fall through as offline. The Paper
 * side then asks them for a password (if registered) or to /register.
 *
 * Mojang lookup runs as a blocking await inside an EventTask.async() block —
 * Velocity holds the login flow until the future resolves. Capped at 5s to
 * avoid hanging connections forever; on timeout we err on the side of CRACKED
 * (no force) so that the player at least reaches the password prompt.
 */
public final class AuthBridge {

    private static final long MOJANG_AWAIT_MS = 5000;

    private final MojangApi mojang;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public AuthBridge(MojangApi mojang, Logger logger) {
        this.mojang = mojang;
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) return null;
        String name = event.getUsername();
        if (name == null || name.isBlank()) return null;

        return EventTask.resumeWhenComplete(
                mojang.lookup(name)
                        .orTimeout(MOJANG_AWAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .handle((status, err) -> {
                            if (err != null) {
                                logger.warn("Mojang await failed for '{}', falling back to offline: {}",
                                        name, err.getClass().getSimpleName());
                                return null;
                            }
                            if (status == MojangApi.Status.PREMIUM) {
                                event.setResult(PreLoginComponentResult.forceOnlineMode());
                                logger.info("Forcing online-mode for premium name '{}'", name);
                            }
                            return null;
                        })
        );
    }

    @SuppressWarnings("unused")
    private Component kick(String mini) {
        return mm.deserialize(mini);
    }
}
