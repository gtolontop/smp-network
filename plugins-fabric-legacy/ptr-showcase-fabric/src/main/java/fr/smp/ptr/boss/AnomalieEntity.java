package fr.smp.ptr.boss;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.level.Level;

public class AnomalieEntity extends WitherSkeleton implements PolymerEntity {

    private static final BossPhase[] PHASES = {
            new BossPhase("emerging", 1.00f, 60, new String[]{"laserBeam"}, "ptr:music.anomalie_theme",
                    "...ich existiert. mais en plusieurs dimensions à la fois."),
            new BossPhase("manifest", 0.75f, 45, new String[]{"laserBeam", "ringSeism"}, "ptr:music.anomalie_theme",
                    "[ERREUR] tes coordonnées ne sont plus valides."),
            new BossPhase("fractured", 0.50f, 30, new String[]{"laserBeam", "ringSeism", "groundSlam"}, "ptr:music.anomalie_theme",
                    "[CASCADE] le réel se désagrège autour de moi."),
            new BossPhase("collapsing", 0.25f, 20, new String[]{"laserBeam", "ringSeism", "groundSlam"}, "ptr:music.anomalie_theme",
                    "[FIN] effondrement total — emportez ce qui reste."),
    };

    private final BossPhaseController phaseController;

    public AnomalieEntity(EntityType<? extends WitherSkeleton> type, Level level) {
        super(type, level);
        this.phaseController = new BossPhaseController(this, PHASES);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        phaseController.tick();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 480.0)
                .add(Attributes.ATTACK_DAMAGE, 22.0)
                .add(Attributes.MOVEMENT_SPEED, 0.42)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return EntityType.WITHER_SKELETON;
    }
}
