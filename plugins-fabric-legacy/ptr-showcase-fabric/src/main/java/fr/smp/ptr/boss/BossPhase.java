package fr.smp.ptr.boss;

/**
 * One phase of a multi-phase boss fight. Phases are activated by HP thresholds
 * (in fraction of max HP, descending).
 */
public record BossPhase(
        String name,
        float hpThreshold,
        int telegraphPeriodTicks,
        String[] telegraphIds,
        String musicTrack,
        String chatBarkOnEnter
) {
    public static BossPhase passive(String name, float threshold) {
        return new BossPhase(name, threshold, 0, new String[]{}, null, null);
    }
}
