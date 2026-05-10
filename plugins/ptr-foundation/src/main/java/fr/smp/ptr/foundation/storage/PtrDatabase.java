package fr.smp.ptr.foundation.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.NotNull;

/**
 * Owns the SQLite {@link DataSource} and the Flyway migration runner.
 *
 * <p>SQLite is a single-writer engine, so {@link HikariConfig#setMaximumPoolSize(int)}
 * defaults to 1. Bumping the pool only makes sense once a future migration
 * enables WAL mode and dedicated read connections.
 */
public final class PtrDatabase {

    private final HikariDataSource dataSource;
    private final Logger logger;

    private PtrDatabase(@NotNull HikariDataSource dataSource, @NotNull Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Open a SQLite database at the given path, run Flyway migrations if
     * requested, and return the live handle.
     */
    public static @NotNull PtrDatabase open(
            @NotNull Path dbFile, int poolSize, boolean runMigrations, @NotNull Logger logger)
            throws SQLException {
        Objects.requireNonNull(dbFile, "dbFile");
        Objects.requireNonNull(logger, "logger");
        try {
            Files.createDirectories(dbFile.getParent());
        } catch (java.io.IOException e) {
            throw new SQLException("Cannot create parent directory for " + dbFile, e);
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        cfg.setMaximumPoolSize(Math.max(1, poolSize));
        cfg.setPoolName("ptr-foundation-sqlite");
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setAutoCommit(true);
        // SQLite needs serialised access — leave maxLifetime generous and
        // connection-timeout short so a deadlock surfaces as a fail-fast.
        cfg.setConnectionTimeout(5_000);
        cfg.setMaxLifetime(1_800_000);

        HikariDataSource ds = new HikariDataSource(cfg);
        if (runMigrations) {
            Flyway flyway =
                    Flyway.configure(PtrDatabase.class.getClassLoader())
                            .dataSource(ds)
                            .locations("classpath:db/migration")
                            .baselineOnMigrate(true)
                            .load();
            int applied = flyway.migrate().migrationsExecuted;
            logger.info(() -> "PtrDatabase: Flyway applied " + applied + " migration(s)");
        }
        return new PtrDatabase(ds, logger);
    }

    /** Borrow a connection from the pool. Caller closes. */
    public @NotNull Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Underlying datasource — exposed for tests and instrumentation. */
    public @NotNull DataSource dataSource() {
        return dataSource;
    }

    /** Close the pool. */
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
            logger.info("PtrDatabase: closed");
        }
    }
}
