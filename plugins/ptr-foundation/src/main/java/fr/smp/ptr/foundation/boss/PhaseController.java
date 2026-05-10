package fr.smp.ptr.foundation.boss;

import fr.smp.ptr.foundation.platform.SchedulerService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Drives HP-based phase transitions for a single boss instance.
 *
 * <p>The controller polls the entity's HP every tick (entity scheduler) and
 * advances {@code currentPhase} whenever {@code health <= hp_threshold} of
 * the next phase. Phase transitions broadcast an Adventure {@link
 * Component} bark to nearby players via the provided {@link Audience}.
 */
public final class PhaseController {

    private final BossDefinition definition;
    private final LivingEntity entity;
    private final SchedulerService scheduler;
    private final Audience audience;
    private int currentPhase = 0;
    private @org.jetbrains.annotations.Nullable ScheduledTask tickTask;

    public PhaseController(
            @NotNull BossDefinition definition,
            @NotNull LivingEntity entity,
            @NotNull SchedulerService scheduler,
            @NotNull Audience audience) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.audience = Objects.requireNonNull(audience, "audience");
    }

    /** Begin ticking; fires {@link BossPhase#onEnter()} for the initial phase. */
    public void start() {
        if (tickTask != null) {
            return;
        }
        enterPhase(0);
        tickTask = scheduler.runOnEntity(entity, this::tick, null);
    }

    /** Stop ticking and clear scheduled work. */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        if (!entity.isValid() || entity.isDead()) {
            stop();
            return;
        }
        double hp = entity.getHealth();
        List<BossPhase> phases = definition.phases();
        // Find the latest phase whose threshold is >= current hp.
        for (int i = phases.size() - 1; i > currentPhase; i--) {
            if (hp <= phases.get(i).hpThreshold()) {
                enterPhase(i);
                break;
            }
        }
        BossPhase phase = phases.get(currentPhase);
        if (phase.onTick() != null) {
            phase.onTick().accept(entity);
        }
    }

    private void enterPhase(int index) {
        currentPhase = index;
        BossPhase phase = definition.phases().get(index);
        Component bark =
                Component.text("» ", NamedTextColor.DARK_RED)
                        .append(definition.displayName().color(NamedTextColor.GOLD))
                        .append(Component.text(" entre en phase ", NamedTextColor.GRAY))
                        .append(phase.name().color(NamedTextColor.YELLOW));
        audience.sendMessage(bark);
        if (phase.onEnter() != null) {
            phase.onEnter().accept(entity);
        }
        // Snap attributes back to definition values — phase transitions
        // could rewrite max health / attack damage in future content layers.
        definition
                .attributes()
                .forEach(
                        (attr, val) -> {
                            var inst = entity.getAttribute(attr);
                            if (inst != null) {
                                inst.setBaseValue(val);
                            }
                        });
    }

    /** Currently active phase. */
    public @NotNull BossPhase currentPhase() {
        return definition.phases().get(currentPhase);
    }

    /** Index of the currently active phase. */
    public int currentPhaseIndex() {
        return currentPhase;
    }
}
