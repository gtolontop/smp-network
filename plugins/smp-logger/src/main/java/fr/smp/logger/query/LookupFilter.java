package fr.smp.logger.query;

import fr.smp.logger.model.Action;

/** Parsed filter set for a /lookup query. Null fields = wildcard. */
public final class LookupFilter {
    public Integer playerId;
    public Action action;
    public Integer materialId;
    public Long sinceMs;
    public Long untilMs;
    public Integer worldId;
    public Integer x, y, z;
    public Integer radius;
    public int limit = 50;
    public int page = 1;

    public boolean hasLocationFilter() {
        return worldId != null && x != null && z != null && radius != null;
    }
}
