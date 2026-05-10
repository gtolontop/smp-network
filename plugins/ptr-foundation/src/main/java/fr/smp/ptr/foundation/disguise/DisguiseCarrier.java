package fr.smp.ptr.foundation.disguise;

import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy for hiding a {@code ptr:} block (or mob) behind a vanilla
 * registry member.
 *
 * <p>Folia + custom plugin can't ship real {@code ptr:my_block} ids over the
 * wire — the block registry is hard-coded on the vanilla client. We
 * therefore disguise every custom block as some vanilla block that has
 * enough "invalid" states for us to overload (note_block tuning, mushroom
 * faces, tripwire, ...). Each carrier here documents:
 *
 * <ul>
 *   <li><b>capacity</b> — how many distinct {@code ptr:} ids the disguise
 *       can carry without colliding;
 *   <li><b>known leaks</b> — vanilla interactions that resync the carrier
 *       back to a "valid" state and therefore destroy our disguise.
 * </ul>
 *
 * @param <S> state type used to key a specific carrier "slot"
 */
public interface DisguiseCarrier<S> {

    /** Short identifier (used for telemetry and PDC tagging). */
    @NotNull String kind();

    /**
     * Maximum number of distinct foundation ids this carrier can disguise
     * without overlap. Hard upper bound — content layers should not register
     * more entries than this.
     */
    int capacity();

    /**
     * Vanilla interactions that resync the carrier back to a "valid" state
     * and therefore destroy our disguise. Listeners in the disguise package
     * defend against the ones we can catch; the rest are accepted trade-offs.
     */
    @NotNull List<String> knownLeaks();

    /** Materialise the disguise at {@code loc} with the given state. */
    void place(@NotNull Location loc, @NotNull S state);

    /** Read the state at {@code loc}, or {@link Optional#empty()} if the carrier is not present. */
    @NotNull Optional<S> readState(@NotNull Location loc);

    /** Revert the carrier at {@code loc} to plain {@code AIR}. */
    void clear(@NotNull Location loc);
}
