package fr.smp.ptr.foundation.telemetry;

import fr.smp.ptr.foundation.platform.SchedulerService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Periodically dumps the {@link PtrMetrics} snapshot to the plugin logger.
 *
 * <p>The dump is one line per call, format:
 *
 * <pre>{@code
 * [PTR-metrics] counters={a=1, b=2} gauges={c=3} timers={d.count=4, d.avg_ns=...}
 * }</pre>
 *
 * <p>{@code json-logs: true} switches the format to a one-line JSON object
 * (no shaded JSON lib — kept manual because the metric vocabulary is small).
 */
public final class PtrTelemetryService {

    private final PtrMetrics metrics;
    private final SchedulerService scheduler;
    private final Logger logger;
    private final boolean json;
    private final int intervalSeconds;
    private @org.jetbrains.annotations.Nullable ScheduledTask task;

    public PtrTelemetryService(
            @NotNull PtrMetrics metrics,
            @NotNull SchedulerService scheduler,
            @NotNull Logger logger,
            boolean json,
            int intervalSeconds) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.json = json;
        this.intervalSeconds = Math.max(0, intervalSeconds);
    }

    /** Schedule the periodic dump. Idempotent. */
    public void start() {
        if (task != null || intervalSeconds <= 0) {
            return;
        }
        long ticks = (long) intervalSeconds * 20L;
        task = scheduler.runOnGlobalTimer(this::dumpOnce, ticks, ticks);
    }

    /** Cancel the periodic dump. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Force a dump right now. */
    public void dumpOnce() {
        Map<String, Long> c = metrics.snapshotCounters();
        Map<String, Long> g = metrics.snapshotGauges();
        Map<String, PtrMetrics.TimerStats> t = metrics.snapshotTimers();
        if (json) {
            StringBuilder sb = new StringBuilder("{\"counters\":");
            appendMap(sb, c);
            sb.append(",\"gauges\":");
            appendMap(sb, g);
            sb.append(",\"timers\":{");
            boolean first = true;
            for (Map.Entry<String, PtrMetrics.TimerStats> e : t.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(e.getKey())).append("\":{")
                        .append("\"count\":").append(e.getValue().count())
                        .append(",\"total_ns\":").append(e.getValue().totalNanos())
                        .append(",\"max_ns\":").append(e.getValue().maxNanos())
                        .append(",\"avg_ns\":").append(e.getValue().avgNanos())
                        .append('}');
            }
            sb.append("}}");
            logger.info(sb::toString);
        } else {
            logger.info(
                    () ->
                            "[PTR-metrics] counters="
                                    + c
                                    + " gauges="
                                    + g
                                    + " timers="
                                    + summariseTimers(t));
        }
    }

    private static String summariseTimers(Map<String, PtrMetrics.TimerStats> timers) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, PtrMetrics.TimerStats> e : timers.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey())
                    .append("=[count=")
                    .append(e.getValue().count())
                    .append(",avg_ns=")
                    .append(e.getValue().avgNanos())
                    .append(",max_ns=")
                    .append(e.getValue().maxNanos())
                    .append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendMap(StringBuilder sb, Map<String, Long> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append('}');
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
