package fr.smp.ptr.foundation.registry;

/** Registry of foundation-managed custom damage types. Empty by default. */
public final class PtrDamageTypeRegistry extends PtrRegistry<PtrDamageTypeDef> {

    public PtrDamageTypeRegistry() {
        super("damage_type");
    }
}
