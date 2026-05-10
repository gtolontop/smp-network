package fr.smp.ptr.foundation.platform;

import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

/**
 * Fail-fast check that the host is Folia, not vanilla Paper.
 *
 * <p>The foundation calls {@code Bukkit.getRegionScheduler()} and {@code
 * Bukkit.isOwnedByCurrentRegion(...)} freely. On Paper these are shims that
 * pretend everything is the main thread, which hides region-ownership bugs
 * during development. Refuse to enable instead of silently degrading.
 */
public final class PaperVersionGuard {

    private PaperVersionGuard() {}

    /**
     * Verifies the runtime is Folia. Returns the reported server version
     * string on success; throws {@link IllegalStateException} on Paper or
     * any other Bukkit fork.
     */
    public static @NotNull String verifyFolia(@NotNull Logger logger) {
        String name = Bukkit.getName();
        String version = Bukkit.getVersion();
        boolean folia = name != null && name.toLowerCase(Locale.ROOT).contains("folia");
        if (!folia) {
            throw new IllegalStateException(
                    "PtrFoundation requires Folia (Bukkit.getName()='" + name
                            + "', Bukkit.getVersion()='" + version
                            + "'). Refusing to enable. Switch the backend in ptr/ "
                            + "to a Folia 26.1.2 build or use the legacy Paper plugin.");
        }
        logger.info(() -> "PaperVersionGuard: Folia detected — " + name + " / " + version);
        return version;
    }
}
