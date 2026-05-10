package fr.smp.ptr.foundation.telemetry;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.storage.PtrDatabase;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Append-only audit trail for foundation admin operations.
 *
 * <p>Each row is one ({@code timestamp}, {@code actor}, {@code op_type},
 * {@code target_id}, {@code payload_json}) tuple. Writes are batched —
 * {@link #record(UUID, String, String, String)} enqueues, the
 * scheduled flush drains.
 *
 * <p>The foundation wires this up but does not yet emit audit entries —
 * content layers do, once the admin commands land.
 */
public final class PtrAuditLog {

    private static final String INSERT_SQL =
            "INSERT INTO ptr_audit (ts_millis, actor_uuid, op_type, target_id, payload_json) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private final PtrDatabase database;
    private final SchedulerService scheduler;
    private final Logger logger;
    private final int batchSize;
    private final int intervalSeconds;

    private final Deque<Entry> queue = new ArrayDeque<>();
    private final Object lock = new Object();
    private @Nullable ScheduledTask task;

    private record Entry(
            long tsMillis,
            @Nullable UUID actorUuid,
            String opType,
            @Nullable String targetId,
            @Nullable String payloadJson) {}

    public PtrAuditLog(
            @NotNull PtrDatabase database,
            @NotNull SchedulerService scheduler,
            @NotNull Logger logger,
            int batchSize,
            int intervalSeconds) {
        this.database = Objects.requireNonNull(database, "database");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.batchSize = Math.max(1, batchSize);
        this.intervalSeconds = Math.max(1, intervalSeconds);
    }

    /** Start the flush timer. Idempotent. */
    public void start() {
        if (task != null) {
            return;
        }
        long millis = (long) intervalSeconds * 1000L;
        task = scheduler.runAsyncTimer(this::flush, millis, millis);
    }

    /** Stop the flush timer and drain pending entries. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flush();
    }

    /** Enqueue one entry. Triggers an immediate flush if the batch is full. */
    public void record(
            @Nullable UUID actor,
            @NotNull String opType,
            @Nullable String targetId,
            @Nullable String payloadJson) {
        Objects.requireNonNull(opType, "opType");
        boolean shouldFlush;
        synchronized (lock) {
            queue.add(
                    new Entry(System.currentTimeMillis(), actor, opType, targetId, payloadJson));
            shouldFlush = queue.size() >= batchSize;
        }
        if (shouldFlush) {
            scheduler.runAsync(this::flush);
        }
    }

    private void flush() {
        List<Entry> snapshot;
        synchronized (lock) {
            if (queue.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(queue);
            queue.clear();
        }
        try (Connection c = database.connection();
                PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            for (Entry e : snapshot) {
                ps.setLong(1, e.tsMillis());
                ps.setString(2, e.actorUuid() == null ? null : e.actorUuid().toString());
                ps.setString(3, e.opType());
                ps.setString(4, e.targetId());
                ps.setString(5, e.payloadJson());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            logger.warning(
                    () ->
                            "PtrAuditLog: failed to flush "
                                    + snapshot.size()
                                    + " entries: "
                                    + ex.getMessage());
            // Re-queue so we don't drop on transient errors.
            synchronized (lock) {
                snapshot.forEach(queue::addFirst);
            }
        }
    }
}
