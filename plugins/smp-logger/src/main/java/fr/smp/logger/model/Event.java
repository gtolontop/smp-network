package fr.smp.logger.model;

/**
 * Compact in-memory event. Mutable POJO so the event queue can pool and reuse
 * instances without allocation pressure. Set unused fields to 0 / null.
 *
 * Layout matches the events_YYYYMMDD table 1:1.
 */
public final class Event {
    public long timestampMs;   // wall clock, used to pick partition + compute t (sec from midnight)
    public Action action;
    public int actorId;        // PlayerDict id, 0 if none
    public int targetId;       // PlayerDict id, 0 if none
    public int worldId;        // WorldDict id, 0 if N/A
    public int x, y, z;        // block coords
    public int materialId;     // MaterialDict id, 0 if N/A
    public int amount;         // item qty / xp / damage / etc.
    public byte[] itemHash;    // 16 bytes — only for precious items, else null
    public int textId;         // StringDict id, 0 if N/A
    public int meta;           // generic flag/aux field — interpretation per Action

    public void reset() {
        timestampMs = 0;
        action = null;
        actorId = 0;
        targetId = 0;
        worldId = 0;
        x = 0; y = 0; z = 0;
        materialId = 0;
        amount = 0;
        itemHash = null;
        textId = 0;
        meta = 0;
    }
}
