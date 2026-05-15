package fr.smp.ptr.foundation.registry;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Definition of a foundation-managed custom enchantment.
 *
 * <p>The creation kit keeps only the server-side handle that listeners and
 * tooltip overrides hang off. Future content modules own any runtime backing.
 */
public record PtrEnchantDef(@NotNull NamespacedKey id, @NotNull Component displayName)
        implements PtrIdentified {

    public PtrEnchantDef {
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

        public @NotNull PtrEnchantDef build() {
            return new PtrEnchantDef(id, displayName);
        }
    }
}
