package fr.smp.ptr.foundation.api;

import fr.smp.ptr.foundation.registry.PtrMobDef;
import fr.smp.ptr.foundation.registry.PtrMobRegistry;
import fr.smp.ptr.foundation.storage.PtrPdcCodec;
import fr.smp.ptr.foundation.storage.PtrPdcKeys;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/** Flat facade over {@link PtrMobRegistry} + PDC entity-id lookups. */
public final class PtrEntities {

    private PtrEntities() {}

    public static @NotNull Map<NamespacedKey, PtrMobDef> all() {
        return PtrFoundationApi.services().get(PtrMobRegistry.class).view();
    }

    public static @NotNull Optional<PtrMobDef> find(@NotNull NamespacedKey id) {
        return PtrFoundationApi.services().get(PtrMobRegistry.class).get(id);
    }

    /** True if the entity carries a foundation mob id in its PDC. */
    public static boolean isCustomEntity(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return PtrPdcCodec.STRING.has(entity.getPersistentDataContainer(), PtrPdcKeys.MOB_ID);
    }

    /** Read the foundation mob id from an entity's PDC, if any. */
    public static @NotNull Optional<NamespacedKey> readEntityId(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return PtrPdcCodec.NAMESPACED_KEY.readOpt(
                entity.getPersistentDataContainer(), PtrPdcKeys.MOB_ID);
    }

    /** Resolve an entity to its {@link PtrMobDef}. */
    public static @NotNull Optional<PtrMobDef> byEntity(@NotNull Entity entity) {
        return readEntityId(entity).flatMap(PtrEntities::find);
    }
}
