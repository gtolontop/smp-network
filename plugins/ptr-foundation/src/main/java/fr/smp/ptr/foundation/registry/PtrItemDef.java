package fr.smp.ptr.foundation.registry;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Definition of a foundation-managed custom item. Minimal until content layers
 * add carrier {@code Material}, components, attribute modifiers.
 */
public record PtrItemDef(@NotNull NamespacedKey id, @NotNull Component displayName)
        implements PtrIdentified {

    public PtrItemDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

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

        public @NotNull PtrItemDef build() {
            return new PtrItemDef(id, displayName);
        }
    }
}
