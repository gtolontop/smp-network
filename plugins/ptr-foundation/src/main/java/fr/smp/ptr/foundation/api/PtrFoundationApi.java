package fr.smp.ptr.foundation.api;

import fr.smp.ptr.foundation.PtrServices;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Public entry point for foundation services from outside the plugin.
 *
 * <p>Resolved once at plugin enable time and torn down at disable. Content
 * layers and downstream plugins query it via the facades in this package
 * ({@link PtrBlocks}, {@link PtrItems}, {@link PtrEntities},
 * {@link PtrSkills}) — those are thin static helpers that delegate here.
 *
 * <p>Throws {@link IllegalStateException} if accessed before enable or
 * after disable — fail-fast over silent NPE.
 */
public final class PtrFoundationApi {

    private static volatile PtrServices services;

    private PtrFoundationApi() {}

    /**
     * Internal hook called from {@code PtrFoundationPlugin#onEnable()}.
     * Do not call from content code.
     */
    public static void init(@NotNull PtrServices toRegister) {
        Objects.requireNonNull(toRegister, "services");
        if (services != null) {
            throw new IllegalStateException("PtrFoundationApi already initialised");
        }
        services = toRegister;
    }

    /** Internal hook called from {@code PtrFoundationPlugin#onDisable()}. */
    public static void shutdown() {
        services = null;
    }

    /** True between {@link #init} and {@link #shutdown}. */
    public static boolean isReady() {
        return services != null;
    }

    /** Foundation service locator. Throws if not initialised. */
    public static @NotNull PtrServices services() {
        PtrServices snapshot = services;
        if (snapshot == null) {
            throw new IllegalStateException(
                    "PtrFoundationApi not initialised — make sure PtrFoundation is enabled before"
                            + " your plugin and depends on it in plugin.yml");
        }
        return snapshot;
    }

    /** Same as {@link #services} but returns {@link Optional#empty()} instead of throwing. */
    public static @NotNull Optional<PtrServices> servicesOpt() {
        return Optional.ofNullable(services);
    }
}
