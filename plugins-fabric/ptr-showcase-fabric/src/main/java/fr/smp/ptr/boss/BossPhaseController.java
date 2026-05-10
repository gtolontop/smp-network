package fr.smp.ptr.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Tracks a boss's current phase and transitions when HP crosses thresholds.
 * Each phase declares an HP fraction below which it activates; phases must
 * be passed in descending threshold order (e.g. 1.0 → 0.66 → 0.33 → 0.0).
 *
 * <p>Hook: call {@link #tick()} every server tick from the entity's
 * {@code customServerAiStep()} or attach via a server lifecycle listener.</p>
 */
public final class BossPhaseController {

    private final LivingEntity boss;
    private final BossPhase[] phases;
    private int currentIndex = -1;

    public BossPhaseController(LivingEntity boss, BossPhase[] phases) {
        this.boss = boss;
        this.phases = phases;
    }

    public BossPhase currentPhase() {
        return currentIndex >= 0 && currentIndex < phases.length ? phases[currentIndex] : null;
    }

    /** Drive transitions; returns true if the phase changed this tick. */
    public boolean tick() {
        float fraction = boss.getHealth() / boss.getMaxHealth();
        int desired = -1;
        for (int i = 0; i < phases.length; i++) {
            if (fraction <= phases[i].hpThreshold()) {
                desired = i;
            }
        }
        if (desired != currentIndex) {
            currentIndex = desired;
            BossPhase entered = currentPhase();
            if (entered != null && entered.chatBarkOnEnter() != null) {
                broadcastBark(entered.chatBarkOnEnter());
            }
            return true;
        }
        return false;
    }

    private void broadcastBark(String message) {
        if (boss.level().isClientSide()) return;
        Component chat = Component.literal("§6§l[" + boss.getName().getString() + "] §f" + message);
        boss.level().players().forEach(p -> {
            if (p.distanceTo(boss) <= 64.0 && p instanceof Player) {
                p.sendSystemMessage(chat);
            }
        });
    }
}
