package fr.smp.ptr.foundation.registry;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for every value stored in a {@link PtrRegistry}.
 *
 * <p>The {@code id()} is the registry key (always in the {@code ptr:}
 * namespace), and {@code displayName()} is the player-facing label used by
 * commands, tooltips and audit logs. Both must be non-null.
 */
public interface PtrIdentified {

    /** Registry key in the {@code ptr:} namespace. */
    @NotNull NamespacedKey id();

    /** Adventure {@link Component} shown to players. Should not be a legacy {@link String}. */
    @NotNull Component displayName();
}
