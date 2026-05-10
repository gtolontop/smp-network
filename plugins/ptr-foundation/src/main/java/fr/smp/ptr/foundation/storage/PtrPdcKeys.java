package fr.smp.ptr.foundation.storage;

import fr.smp.ptr.foundation.registry.PtrIds;
import org.bukkit.NamespacedKey;

/**
 * Centralised {@link NamespacedKey} constants used by the disguise and
 * registry layers when reading or writing to a {@link
 * org.bukkit.persistence.PersistentDataContainer}.
 *
 * <p>Storing every key in one class prevents typos at call sites and makes
 * it easy to audit what foundation data ends up persisted on items, blocks
 * and entities.
 */
public final class PtrPdcKeys {

    private PtrPdcKeys() {}

    /** Identifies a {@code ptr:} block placed in the world via a disguise carrier. */
    public static final NamespacedKey BLOCK_ID = PtrIds.key("block_id");

    /** Identifies a {@code ptr:} item (foundation-managed). */
    public static final NamespacedKey ITEM_ID = PtrIds.key("item_id");

    /** Identifies a {@code ptr:} mob (foundation-managed). */
    public static final NamespacedKey MOB_ID = PtrIds.key("mob_id");

    /** Identifies a {@code ptr:} boss instance. */
    public static final NamespacedKey BOSS_ID = PtrIds.key("boss_id");

    /** Stores the current boss phase index. */
    public static final NamespacedKey BOSS_PHASE = PtrIds.key("boss_phase");

    /** Stores a charge value (energy/durability replacement) on items. */
    public static final NamespacedKey ITEM_CHARGE = PtrIds.key("item_charge");

    /** Stores the disguise carrier kind ({@code "note_block"}, {@code "mushroom"}, ...) used. */
    public static final NamespacedKey CARRIER_KIND = PtrIds.key("carrier_kind");
}
