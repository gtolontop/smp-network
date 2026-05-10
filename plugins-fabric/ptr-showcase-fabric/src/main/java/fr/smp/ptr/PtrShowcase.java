package fr.smp.ptr;

import fr.smp.ptr.boss.BossManager;
import fr.smp.ptr.command.PtrCommand;
import fr.smp.ptr.item.PtrItems;
import fr.smp.ptr.block.PtrBlocks;
import fr.smp.ptr.entity.PtrEntities;
import fr.smp.ptr.damage.PtrDamageTypes;
import fr.smp.ptr.boss.PhaseController;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PtrShowcase implements ModInitializer {

    public static final String MOD_ID = "ptr";
    public static final Logger LOGGER = LoggerFactory.getLogger("PTR");

    private static PtrShowcase instance;
    private BossManager bossManager;

    public static PtrShowcase get() {
        return instance;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("PTR Showcase {} bootstrapping (Fabric+Polymer)", PtrShowcase.class.getPackage().getImplementationVersion());

        PtrDamageTypes.bootstrap();
        PtrBlocks.bootstrap();
        PtrItems.bootstrap();
        PtrEntities.bootstrap();

        this.bossManager = new BossManager();

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> PtrCommand.register(dispatcher));

        LOGGER.info("PTR Showcase ready: {} blocks, {} items, {} entities loaded.",
                PtrBlocks.count(), PtrItems.count(), PtrEntities.count());
    }

    public BossManager bossManager() {
        return bossManager;
    }
}
