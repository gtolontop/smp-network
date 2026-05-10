package fr.smp.ptr.foundation.registry;

/** Registry of foundation-managed custom items. Empty by default. */
public final class PtrItemRegistry extends PtrRegistry<PtrItemDef> {

    public PtrItemRegistry() {
        super("item");
    }
}
