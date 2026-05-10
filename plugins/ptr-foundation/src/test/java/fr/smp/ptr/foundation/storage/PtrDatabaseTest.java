package fr.smp.ptr.foundation.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PtrDatabaseTest {

    private static final Logger LOGGER = Logger.getLogger("PtrDatabaseTest");

    private PtrDatabase database;

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void opensAndRunsMigrations(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ptr.db");
        database = PtrDatabase.open(file, 1, true, LOGGER);

        try (Connection c = database.connection();
                Statement s = c.createStatement();
                ResultSet rs =
                        s.executeQuery(
                                "SELECT name FROM sqlite_master WHERE type='table' "
                                        + "AND name IN ('ptr_audit')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("ptr_audit");
        }
    }

    @Test
    void migrationsAreIdempotent(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ptr.db");
        database = PtrDatabase.open(file, 1, true, LOGGER);
        database.close();
        // Re-open — Flyway must not re-apply.
        database = PtrDatabase.open(file, 1, true, LOGGER);
        try (Connection c = database.connection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT count(*) FROM ptr_audit")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(0);
        }
    }
}
