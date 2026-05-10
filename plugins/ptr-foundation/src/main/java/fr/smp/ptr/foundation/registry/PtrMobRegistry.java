package fr.smp.ptr.foundation.registry;

/** Registry of foundation-managed custom mobs. Empty by default. */
public final class PtrMobRegistry extends PtrRegistry<PtrMobDef> {

    public PtrMobRegistry() {
        super("mob");
    }
}
