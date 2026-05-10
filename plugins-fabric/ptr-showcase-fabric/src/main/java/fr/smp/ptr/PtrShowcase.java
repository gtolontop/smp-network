package fr.smp.ptr;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PtrShowcase implements ModInitializer {

    public static final String MOD_ID = "ptr";
    public static final Logger LOGGER = LoggerFactory.getLogger("PTR");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("PTR Showcase bootstrapping (Fabric + Polymer)");

        PtrBlocks.bootstrap();
        PtrItems.bootstrap();

        LOGGER.info("PTR Showcase ready: {} blocks, {} items.",
                PtrBlocks.count(), PtrItems.count());
    }
}
