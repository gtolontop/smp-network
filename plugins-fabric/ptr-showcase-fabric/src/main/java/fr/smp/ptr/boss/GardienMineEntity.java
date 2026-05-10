package fr.smp.ptr.boss;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class GardienMineEntity extends IronGolem implements PolymerEntity {

    private static final BossPhase[] PHASES = {
            new BossPhase("active", 1.00f, 80, new String[]{"ringSeism"}, "ptr:music.gardien_theme",
                    "Tu déranges mon repos, mineur."),
            new BossPhase("alerted", 0.66f, 60, new String[]{"groundSlam", "ringSeism"}, "ptr:music.gardien_theme",
                    "Le filon se referme..."),
            new BossPhase("enraged", 0.33f, 40, new String[]{"groundSlam", "ringSeism", "laserBeam"}, "ptr:music.gardien_theme",
                    "TOUT LE MONDE DESCEND!"),
    };

    private final BossPhaseController phaseController;

    public GardienMineEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        this.phaseController = new BossPhaseController(this, PHASES);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        phaseController.tick();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return EntityType.IRON_GOLEM;
    }
}
