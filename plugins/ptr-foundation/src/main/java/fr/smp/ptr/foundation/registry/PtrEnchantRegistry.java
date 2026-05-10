package fr.smp.ptr.foundation.registry;

/** Registry of foundation-managed custom enchantments. Empty by default. */
public final class PtrEnchantRegistry extends PtrRegistry<PtrEnchantDef> {

    public PtrEnchantRegistry() {
        super("enchant");
    }
}
