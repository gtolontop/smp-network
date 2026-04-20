package fr.smp.anticheat.movement;

/**
 * Per-environment tunable thresholds for the movement validator.
 * Built once from config and looked up by World.Environment per player tick.
 */
public final class MovementProfile {

    public final boolean enabled;
    public final boolean noclipEnabled;
    public final double noclipMaxStep;
    public final boolean speedEnabled;
    public final double speedWalkBps;
    public final double speedSprintBps;
    public final double speedSprintJumpBps;
    public final boolean flyEnabled;
    public final int flyMaxAirborneTicks;
    public final String action;
    public final int violationThreshold;

    public MovementProfile(boolean enabled,
                           boolean noclipEnabled, double noclipMaxStep,
                           boolean speedEnabled, double speedWalkBps,
                           double speedSprintBps, double speedSprintJumpBps,
                           boolean flyEnabled, int flyMaxAirborneTicks,
                           String action, int violationThreshold) {
        this.enabled = enabled;
        this.noclipEnabled = noclipEnabled;
        this.noclipMaxStep = noclipMaxStep;
        this.speedEnabled = speedEnabled;
        this.speedWalkBps = speedWalkBps;
        this.speedSprintBps = speedSprintBps;
        this.speedSprintJumpBps = speedSprintJumpBps;
        this.flyEnabled = flyEnabled;
        this.flyMaxAirborneTicks = flyMaxAirborneTicks;
        this.action = action;
        this.violationThreshold = violationThreshold;
    }
}
