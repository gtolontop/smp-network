package fr.smp.ptr.boss;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.level.Level;

public class AnomalieEntity extends WitherSkeleton implements PolymerEntity {

    public AnomalieEntity(EntityType<? extends WitherSkeleton> type, Level level) {
        super(type, level);
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
