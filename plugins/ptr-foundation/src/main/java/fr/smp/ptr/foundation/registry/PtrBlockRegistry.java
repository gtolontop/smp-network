package fr.smp.ptr.foundation.registry;

/**
 * Registry of foundation-managed custom blocks. Empty by default — content
 * layers register {@link PtrBlockDef}s here at enable time.
 */
public final class PtrBlockRegistry extends PtrRegistry<PtrBlockDef> {

    public PtrBlockRegistry() {
        super("block");
    }
}
