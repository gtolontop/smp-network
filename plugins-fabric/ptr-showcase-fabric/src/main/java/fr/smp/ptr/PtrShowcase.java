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
        PtrEntities.bootstrap();
        PtrCommand.bootstrap();

        // Bundle this mod's assets/ptr/* directory into Polymer's auto-host pack.
        // Pack stays optional: a failed download leaves the player connected
        // (they just see vanilla item visuals for our polymer items until it loads).
        PolymerResourcePackUtils.addModAssets(FABRIC_MOD_ID);

        LOGGER.info("PTR Showcase ready: {} blocks, {} items, {} entity types, resource pack auto-host enabled.",
                PtrBlocks.count(), PtrItems.count(), PtrEntities.count());
    }
}
