package fr.smp.ptr.foundation.model;

import fr.smp.ptr.foundation.registry.PtrRegistry;

/**
 * Registry of parsed {@link Blueprint}s. Content layers load
 * {@code .bbmodel} files at enable time, parse them via
 * {@link fr.smp.ptr.foundation.model.parser.BlockbenchParser}, and register
 * the resulting blueprint here. Mob spawn code then instantiates an
 * {@link ActiveModel} from the blueprint.
 */
public final class PtrModelRegistry extends PtrRegistry<Blueprint> {

    public PtrModelRegistry() {
        super("model");
    }
}
