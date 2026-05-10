package fr.smp.logger.relations;

public enum RelationSignal {
    PROXIMITY("proximity_seconds", 1),
    SHARED_CONTAINER("shared_container_count", 3),
    SHARED_FURNACE("shared_furnace_count", 5),
    CHEST_HANDOFF("chest_handoff_count", 7),
    TRADE("trade_count", 6),
    COMBAT_ASSIST("combat_assist_count", 8),
    HOME_NEAR("home_near_count", 12),
    PVP_CONTACT("pvp_contact_count", -4),
    ADMIN_MARK("evidence_count", 0);

    private final String counterColumn;
    private final int defaultPoints;

    RelationSignal(String counterColumn, int defaultPoints) {
        this.counterColumn = counterColumn;
        this.defaultPoints = defaultPoints;
    }

    public String counterColumn() {
        return counterColumn;
    }

    public int defaultPoints() {
        return defaultPoints;
    }
}
