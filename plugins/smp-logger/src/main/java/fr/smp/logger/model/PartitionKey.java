package fr.smp.logger.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Identifies a daily partition table (events_YYYYMMDD). All partition math is
 * UTC so 00:00 wall-clock at any TZ doesn't cause table churn.
 */
public final class PartitionKey {

    public static final ZoneId ZONE = ZoneOffset.UTC;
    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LocalDate date;
    private final String tableName;
    private final long midnightMs;

    public PartitionKey(LocalDate date) {
        this.date = date;
        this.tableName = "events_" + date.format(FMT);
        this.midnightMs = date.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    public static PartitionKey forTimestamp(long ms) {
        return new PartitionKey(LocalDate.ofInstant(java.time.Instant.ofEpochMilli(ms), ZONE));
    }

    public static PartitionKey today() {
        return new PartitionKey(LocalDate.now(ZONE));
    }

    public LocalDate date() { return date; }
    public String table() { return tableName; }

    /** Seconds since this partition's midnight, packed in INT (max 86399). */
    public int secondsOfDay(long ms) {
        return (int) ((ms - midnightMs) / 1000L);
    }

    public long midnightMs() { return midnightMs; }

    @Override public String toString() { return tableName; }
    @Override public boolean equals(Object o) {
        return o instanceof PartitionKey k && k.date.equals(date);
    }
    @Override public int hashCode() { return date.hashCode(); }
}
