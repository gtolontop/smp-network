package fr.smp.ptr;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import fr.smp.ptr.boss.AnomalieEntity;
import fr.smp.ptr.boss.GardienMineEntity;
import fr.smp.ptr.boss.RoiPillardsEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PtrEntities {

    private static final Map<String, EntityType<?>> REGISTERED = new LinkedHashMap<>();

    public static final EntityType<GardienMineEntity> GARDIEN_MINE = register("gardien_mine",
            EntityType.Builder.of(GardienMineEntity::new, MobCategory.MONSTER)
                    .sized(1.4f, 2.7f)
                    .updateInterval(2));

    public static final EntityType<RoiPillardsEntity> ROI_PILLARDS = register("roi_pillards",
            EntityType.Builder.of(RoiPillardsEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .updateInterval(2));

    public static final EntityType<AnomalieEntity> ANOMALIE = register("anomalie",
            EntityType.Builder.of(AnomalieEntity::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.4f)
                    .updateInterval(1));

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        Identifier id = PtrShowcase.id(name);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        EntityType<T> type = builder.build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
        REGISTERED.put(name, type);
        return type;
    }

    public static int count() {
        return REGISTERED.size();
    }

    public static void bootstrap() {
        FabricDefaultAttributeRegistry.register(GARDIEN_MINE, GardienMineEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ROI_PILLARDS, RoiPillardsEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ANOMALIE, AnomalieEntity.createAttributes());

        PolymerEntityUtils.registerType(GARDIEN_MINE, ROI_PILLARDS, ANOMALIE);
    }

    public static EntityType<?> get(String name) {
        return REGISTERED.get(name);
    }

    private PtrEntities() {}
}
