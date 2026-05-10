package fr.smp.ptr;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PtrShowcase implements ModInitializer {

    public static final String MOD_ID = "ptr";
    public static final String FABRIC_MOD_ID = "ptr_showcase";
    public static final Logger LOGGER = LoggerFactory.getLogger("PTR");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("PTR Showcase bootstrapping (Fabric + Polymer)");

        PtrBlocks.bootstrap();
        PtrItems.bootstrap();
        PtrCommand.bootstrap();

        // Bundle this mod's assets/ptr/* directory into Polymer's auto-host pack.
        PolymerResourcePackUtils.addModAssets(FABRIC_MOD_ID);
        PolymerResourcePackUtils.markAsRequired();

        LOGGER.info("PTR Showcase ready: {} blocks, {} items, resource pack auto-host enabled.",
                PtrBlocks.count(), PtrItems.count());
    }
}
