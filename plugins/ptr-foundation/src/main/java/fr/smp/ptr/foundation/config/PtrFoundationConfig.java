package fr.smp.ptr.foundation.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Strongly-typed snapshot of {@code config/foundation.yml}.
 *
 * <p>Configurate deserialises one of these per reload. The instance is
 * immutable from listeners' point of view — {@link PtrConfigService} swaps a
 * fresh instance into a volatile reference atomically.
 */
@ConfigSerializable
public final class PtrFoundationConfig {

    private Telemetry telemetry = new Telemetry();
    private Storage storage = new Storage();
    private Audit audit = new Audit();

    public Telemetry telemetry() {
        return telemetry;
    }

    public Storage storage() {
        return storage;
    }

    public Audit audit() {
        return audit;
    }

    /** Telemetry section. */
    @ConfigSerializable
    public static final class Telemetry {
        private boolean jsonLogs = false;
        private int metricDumpIntervalSeconds = 60;

        public boolean jsonLogs() {
            return jsonLogs;
        }

        public int metricDumpIntervalSeconds() {
            return metricDumpIntervalSeconds;
        }
    }

    /** Storage section. */
    @ConfigSerializable
    public static final class Storage {
        private String sqliteFile = "data/ptr.db";
        private int poolSize = 1;
        private boolean runMigrations = true;

        public String sqliteFile() {
            return sqliteFile;
        }

        public int poolSize() {
            return poolSize;
        }

        public boolean runMigrations() {
            return runMigrations;
        }
    }

    /** Audit section. */
    @ConfigSerializable
    public static final class Audit {
        private int flushBatchSize = 64;
        private int flushIntervalSeconds = 30;

        public int flushBatchSize() {
            return flushBatchSize;
        }

        public int flushIntervalSeconds() {
            return flushIntervalSeconds;
        }
    }
}
