package fr.smp.logger.queue;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.db.Database;
import fr.smp.logger.db.PartitionManager;
import fr.smp.logger.model.Action;
import fr.smp.logger.model.Event;
import fr.smp.logger.model.PartitionKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput async event sink.
 *
 * Producers (Bukkit listeners on the main thread) call {@link #submit(Event)}.
 * A single dedicated writer thread drains the queue, groups events by partition
 * (date), and flushes them in one transaction per partition per batch.
 *
 * Backpressure: queue is bounded; if full we drop+count. Dropping events is
 * preferable to stalling the main thread under hostile load — a /loggerstats
 * surfaces the drop counter so admins can tune capacity.
 */
public class EventQueue {

    private final SMPLogger plugin;
    private final Database db;
    private final PartitionManager partitions;

    private final BlockingQueue<Event> queue;
    private final int flushEvents;
    private final long flushMillis;

    private Thread worker;
    private volatile boolean running = false;

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong batchCount = new AtomicLong();
    private volatile long lastFlushMs = 0;

    public EventQueue(SMPLogger plugin, Database db, PartitionManager partitions) {
        this.plugin = plugin;
        this.db = db;
        this.partitions = partitions;
        int capacity = Math.max(1024, plugin.getConfig().getInt("queue.capacity", 65536));
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.flushEvents = Math.max(64, plugin.getConfig().getInt("queue.flush-events", 8192));
        this.flushMillis = Math.max(50L, plugin.getConfig().getLong("queue.flush-millis", 2000L));
    }

    public void start() {
        running = true;
        worker = new Thread(this::loop, "SMPLogger-Writer");
        worker.setDaemon(true);
        worker.start();
        plugin.getLogger().info("EventQueue started (cap=" + queue.remainingCapacity()
                + ", flushEvents=" + flushEvents + ", flushMillis=" + flushMillis + ")");
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        // Final drain so we don't lose anything queued at shutdown.
        try {
            List<Event> rest = new ArrayList<>(queue.size() + 1);
            queue.drainTo(rest);
            if (!rest.isEmpty()) flushBatch(rest);
        } catch (Exception e) {
            plugin.getLogger().warning("Final drain failed: " + e.getMessage());
        }
    }

    /** Non-blocking. Returns true if accepted, false if dropped under backpressure. */
    public boolean submit(Event e) {
        if (e == null || e.action == null) return false;
        if (e.timestampMs == 0) e.timestampMs = System.currentTimeMillis();
        submitted.incrementAndGet();
        if (queue.offer(e)) return true;
        dropped.incrementAndGet();
        return false;
    }

    private void loop() {
        List<Event> batch = new ArrayList<>(flushEvents);
        long lastFlush = System.currentTimeMillis();

        while (running || !queue.isEmpty()) {
            try {
                Event head = queue.poll(flushMillis, TimeUnit.MILLISECONDS);
                if (head != null) batch.add(head);
                queue.drainTo(batch, flushEvents - batch.size());

                long now = System.currentTimeMillis();
                boolean reachedSize = batch.size() >= flushEvents;
                boolean reachedTime = !batch.isEmpty() && (now - lastFlush) >= flushMillis;
                if (reachedSize || (reachedTime)) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlush = now;
                }
            } catch (InterruptedException ie) {
                if (!running) break;
            } catch (Exception ex) {
                plugin.getLogger().warning("Writer loop error: " + ex.getMessage());
            }
        }
    }

    private void flushBatch(List<Event> batch) {
        if (batch.isEmpty()) return;
        // Group by partition so each partition gets one prepared statement reused.
        Map<PartitionKey, List<Event>> byKey = new HashMap<>();
        for (Event e : batch) {
            PartitionKey k = PartitionKey.forTimestamp(e.timestampMs);
            byKey.computeIfAbsent(k, kk -> new ArrayList<>()).add(e);
        }
        try (Connection con = db.writer()) {
            try {
                con.setAutoCommit(false);
                for (var entry : byKey.entrySet()) {
                    PartitionKey key = entry.getKey();
                    String table = partitions.ensure(key);
                    String sql = "INSERT INTO " + table + "(t, action, actor, target, world, x, y, z, "
                            + "material, amount, item_hash, text_id, meta) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        for (Event e : entry.getValue()) {
                            ps.setInt(1, key.secondsOfDay(e.timestampMs));
                            ps.setInt(2, e.action.id());
                            ps.setInt(3, e.actorId);
                            ps.setInt(4, e.targetId);
                            ps.setInt(5, e.worldId);
                            ps.setInt(6, e.x);
                            ps.setInt(7, e.y);
                            ps.setInt(8, e.z);
                            ps.setInt(9, e.materialId);
                            ps.setInt(10, e.amount);
                            if (e.itemHash != null) ps.setBytes(11, e.itemHash);
                            else ps.setNull(11, java.sql.Types.BLOB);
                            ps.setInt(12, e.textId);
                            ps.setInt(13, e.meta);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                con.commit();
                written.addAndGet(batch.size());
                batchCount.incrementAndGet();
                lastFlushMs = System.currentTimeMillis();
            } catch (SQLException e) {
                try { con.rollback(); } catch (SQLException ignored) {}
                plugin.getLogger().warning("Batch flush failed (" + batch.size() + " events): " + e.getMessage());
            } finally {
                try { con.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Batch flush failed (" + batch.size() + " events): " + e.getMessage());
        }
    }

    // ---------- stats ----------
    public long submitted() { return submitted.get(); }
    public long written() { return written.get(); }
    public long dropped() { return dropped.get(); }
    public long batches() { return batchCount.get(); }
    public int queued() { return queue.size(); }
    public long lastFlushMs() { return lastFlushMs; }
}
