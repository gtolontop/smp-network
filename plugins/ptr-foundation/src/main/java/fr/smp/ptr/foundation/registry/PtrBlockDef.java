package fr.smp.ptr.foundation.registry;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Definition of a foundation-managed custom block.
 *
 * <p>This record is intentionally minimal: the foundation ships zero blocks.
 * Concrete content layers will subclass / wrap this with carrier and texture
 * metadata once the disguise layer is consumed.
 */
public record PtrBlockDef(@NotNull NamespacedKey id, @NotNull Component displayName)
        implements PtrIdentified {

    public PtrBlockDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** Fluent builder. Call {@link #id(NamespacedKey)} and {@link #displayName(Component)}. */
    public static final class Builder {

        private NamespacedKey id;
        private Component displayName;

        private Builder() {}

        public @NotNull Builder id(@NotNull NamespacedKey id) {
            this.id = id;
            return this;
        }

        public @NotNull Builder id(@NotNull String path) {
            this.id = PtrIds.key(path);
            return this;
        }

        public @NotNull Builder displayName(@NotNull Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public @NotNull PtrBlockDef build() {
            return new PtrBlockDef(id, displayName);
        }
    }
}
