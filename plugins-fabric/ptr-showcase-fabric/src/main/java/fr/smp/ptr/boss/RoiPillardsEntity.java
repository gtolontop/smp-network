package fr.smp.ptr.boss;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.Level;

public class RoiPillardsEntity extends Pillager implements PolymerEntity {

    private static final BossPhase[] PHASES = {
            new BossPhase("vanguard", 1.00f, 80, new String[]{"laserBeam"}, "ptr:music.roi_theme",
                    "Avant que le sang coule, fuyez."),
            new BossPhase("rallied", 0.66f, 60, new String[]{"laserBeam", "groundSlam"}, "ptr:music.roi_theme",
                    "MES PILLARDS, AU COMBAT!"),
            new BossPhase("desperate", 0.33f, 35, new String[]{"laserBeam", "groundSlam", "ringSeism"}, "ptr:music.roi_theme",
                    "Si je tombe, j'emporte tout avec moi!"),
    };

    private final BossPhaseController phaseController;

    public RoiPillardsEntity(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
        this.phaseController = new BossPhaseController(this, PHASES);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        phaseController.tick();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return EntityType.PILLAGER;
    }
}
