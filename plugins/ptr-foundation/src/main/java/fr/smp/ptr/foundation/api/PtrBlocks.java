package fr.smp.ptr.foundation.api;

import fr.smp.ptr.foundation.registry.PtrBlockDef;
import fr.smp.ptr.foundation.registry.PtrBlockRegistry;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Flat facade over {@link PtrBlockRegistry} + (future) per-block carrier
 * bindings. Inspired by {@code CustomBlock.getInstance(...)} on
 * proprietary plugins — call sites read top-down without navigating the
 * service locator.
 */
public final class PtrBlocks {

    private PtrBlocks() {}

    /** Snapshot of every registered block definition. */
    public static @NotNull Map<NamespacedKey, PtrBlockDef> all() {
        return PtrFoundationApi.services().get(PtrBlockRegistry.class).view();
    }

    /** Lookup by id. */
    public static @NotNull Optional<PtrBlockDef> find(@NotNull NamespacedKey id) {
        return PtrFoundationApi.services().get(PtrBlockRegistry.class).get(id);
    }

    /** True if the id is registered. */
    public static boolean isRegistered(@NotNull NamespacedKey id) {
        return find(id).isPresent();
    }

    /** Total registered blocks. */
    public static int count() {
        return PtrFoundationApi.services().get(PtrBlockRegistry.class).size();
    }
}
