package fr.smp.ptr.foundation.pack;

import fr.smp.ptr.foundation.config.PtrConfigService;
import fr.smp.ptr.foundation.config.PtrFoundationConfig;
import fr.smp.ptr.foundation.platform.SchedulerService;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Pushes the foundation resource pack to joining players (when configured)
 * and exposes manual reload via {@code /ptrf pack reload}.
 *
 * <p>Reads its parameters from {@link PtrFoundationConfig.ResourcePack},
 * which is hot-reloadable: the next join (or a manual reload) picks the
 * fresh URL / SHA1 / prompt without restarting the server.
 */
public final class PtrResourcePackService implements Listener {

    private final PtrConfigService config;
    private final SchedulerService scheduler;
    private final Logger logger;

    public PtrResourcePackService(
            @NotNull PtrConfigService config,
            @NotNull SchedulerService scheduler,
            @NotNull Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** True if both URL and SHA1 are configured. */
    public boolean isConfigured() {
        PtrFoundationConfig.ResourcePack cfg = config.get().resourcePack();
        return !cfg.url().isBlank() && !cfg.sha1().isBlank();
    }

    /** Current configured URL. */
    public @NotNull String url() {
        return config.get().resourcePack().url();
    }

    /** Current configured SHA1. */
    public @NotNull String sha1() {
        return config.get().resourcePack().sha1();
    }

    /** Required? */
    public boolean isRequired() {
        return config.get().resourcePack().required();
    }

    /** Send the pack to a single player (idempotent on the client). */
    public void sendTo(@NotNull Player player) {
        Objects.requireNonNull(player, "player");
        if (!isConfigured()) {
            return;
        }
        PtrFoundationConfig.ResourcePack cfg = config.get().resourcePack();
        Component prompt = LegacyComponentSerializer.legacySection().deserialize(cfg.prompt());
        scheduler.runOnEntity(
                player,
                () -> player.setResourcePack(
                        UUID.nameUUIDFromBytes(cfg.url().getBytes()),
                        cfg.url(),
                        sha1Bytes(cfg.sha1()),
                        prompt,
                        cfg.required()),
                null);
    }

    /** Send the pack to every online player. Used by {@code /ptrf pack reload}. */
    public int sendToAll() {
        if (!isConfigured()) {
            return 0;
        }
        int count = 0;
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            sendTo(p);
            count++;
        }
        return count;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        PtrFoundationConfig.ResourcePack cfg = config.get().resourcePack();
        if (cfg.pushOnJoin() && isConfigured()) {
            sendTo(event.getPlayer());
        }
    }

    private static byte[] sha1Bytes(String hex) {
        if (hex.length() != 40) {
            throw new IllegalStateException(
                    "resource-pack.sha1 must be 40 hex chars, got "
                            + hex.length()
                            + ": '"
                            + hex
                            + "'");
        }
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            out[i] =
                    (byte)
                            ((Character.digit(hex.charAt(i * 2), 16) << 4)
                                    + Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return out;
    }

    /** Logger handle for the {@link PtrFoundationPlugin} wiring. */
    public @NotNull Logger logger() {
        return logger;
    }
}
