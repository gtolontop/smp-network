package fr.smp.ptr.foundation.boss;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrCooldownStore;
import fr.smp.ptr.foundation.skill.PtrSkill;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillTrigger;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drives HP-based phase transitions and skill dispatch for a single boss
 * instance.
 *
 * <p>Each tick (entity scheduler):
 *
 * <ol>
 *   <li>Stops if the entity is dead/invalid (fires {@code OnDeath} skills first).
 *   <li>Checks if HP crossed into a deeper phase; if so, re-enters.
 *   <li>Increments the local tick counter and fires every {@link
 *       TriggeredSkill} whose trigger matches this tick.
 *   <li>Runs the legacy {@code onTick} callback if present.
 * </ol>
 */
public final class PhaseController {

    private final BossDefinition definition;
    private final LivingEntity entity;
    private final SchedulerService scheduler;
    private final Audience audience;
    private final Plugin plugin;
    private final PtrCooldownStore cooldowns = new PtrCooldownStore();

    private int currentPhase = 0;
    private long phaseTick = 0L;
    private final Set<PtrSkillTrigger> onceFiredInPhase = new HashSet<>();
    private @Nullable ScheduledTask tickTask;
    private boolean stopped = false;

    public PhaseController(
            @NotNull BossDefinition definition,
            @NotNull LivingEntity entity,
            @NotNull SchedulerService scheduler,
            @NotNull Audience audience,
            @NotNull Plugin plugin) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Begin ticking; fires {@link BossPhase#onEnter()} for the initial
     * phase, fires every {@code OnSpawn} skill of that phase once, then
     * schedules a recurring entity-scheduler tick.
     */
    public void start() {
        if (tickTask != null) {
            return;
        }
        enterPhase(0);
        fireOnceTriggers(PtrSkillTrigger.OnSpawn.class);
        tickTask = scheduler.runOnEntity(entity, this::tick, null);
    }

    /** Stop ticking, fire OnDeath, clear scheduled work. */
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        fireOnceTriggers(PtrSkillTrigger.OnDeath.class);
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        cooldowns.resetAll(entity.getUniqueId());
    }

    private void tick() {
        if (stopped) {
            return;
        }
        if (!entity.isValid() || entity.isDead()) {
            stop();
            return;
        }

        double hp = entity.getHealth();
        List<BossPhase> phases = definition.phases();
        for (int i = phases.size() - 1; i > currentPhase; i--) {
            if (hp <= phases.get(i).hpThreshold()) {
                enterPhase(i);
                break;
            }
        }

        phaseTick++;
        BossPhase phase = phases.get(currentPhase);

        for (TriggeredSkill ts : phase.skills()) {
            if (shouldFire(ts, hp)) {
                tryCast(ts);
            }
        }

        if (phase.onTick() != null) {
            phase.onTick().accept(entity);
        }
    }

    private boolean shouldFire(TriggeredSkill ts, double hp) {
        return switch (ts.trigger()) {
            case PtrSkillTrigger.OnSpawn ignored -> false; // fired in start()
            case PtrSkillTrigger.OnDeath ignored -> false; // fired in stop()
            case PtrSkillTrigger.OnTimer t -> phaseTick % t.periodTicks() == 0;
            case PtrSkillTrigger.OnHpBelow b ->
                    !onceFiredInPhase.contains(ts.trigger()) && hp <= maxHp() * b.percent();
        };
    }

    private void tryCast(TriggeredSkill ts) {
        if (ts.cooldownMillis() > 0L
                && !cooldowns.tryAcquire(
                        ts.skill().id(), entity.getUniqueId(), ts.cooldownMillis())) {
            return;
        }
        if (ts.trigger() instanceof PtrSkillTrigger.OnHpBelow) {
            onceFiredInPhase.add(ts.trigger());
        }
        PtrSkillContext ctx = new PtrSkillContext(entity, ts.trigger());
        ts.skill().cast(plugin, scheduler, ctx);
    }

    private void fireOnceTriggers(Class<? extends PtrSkillTrigger> triggerClass) {
        BossPhase phase = definition.phases().get(currentPhase);
        for (TriggeredSkill ts : phase.skills()) {
            if (!triggerClass.isInstance(ts.trigger())) {
                continue;
            }
            if (ts.cooldownMillis() > 0L
                    && !cooldowns.tryAcquire(
                            ts.skill().id(), entity.getUniqueId(), ts.cooldownMillis())) {
                continue;
            }
            PtrSkillContext ctx = new PtrSkillContext(entity, ts.trigger());
            ts.skill().cast(plugin, scheduler, ctx);
        }
    }

    private double maxHp() {
        var attr = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : attr.getValue();
    }

    private void enterPhase(int index) {
        currentPhase = index;
        phaseTick = 0L;
        onceFiredInPhase.clear();
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

    /** Entity bound to this controller. */
    public @NotNull LivingEntity entity() {
        return entity;
    }
}
