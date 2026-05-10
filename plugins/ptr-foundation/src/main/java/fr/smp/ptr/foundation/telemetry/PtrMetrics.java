package fr.smp.ptr.foundation.telemetry;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.jetbrains.annotations.NotNull;

/**
 * Lightweight in-memory metrics — Counter / Timer / Gauge.
 *
 * <p>Not a Prometheus replacement. Designed to be cheap to maintain and
 * cheap to dump as a single log line every 60 s via {@link
 * PtrTelemetryService}. Each metric is keyed by a flat string.
 */
public final class PtrMetrics {

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final Map<String, TimerStats> timers = new ConcurrentHashMap<>();

    /** Per-name running stats for {@link #recordTimer(String, long)}. */
    public static final class TimerStats {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong(0L);

        void record(long nanos) {
            count.increment();
            totalNanos.add(nanos);
            maxNanos.accumulateAndGet(nanos, Math::max);
        }

        public long count() {
            return count.sum();
        }

        public long totalNanos() {
            return totalNanos.sum();
        }

        public long maxNanos() {
            return maxNanos.get();
        }

        public long avgNanos() {
            long c = count.sum();
            return c == 0 ? 0L : totalNanos.sum() / c;
        }
    }

    /** Increment a counter by 1. */
    public void incrementCounter(@NotNull String name) {
        addCounter(name, 1L);
    }

    /** Add {@code delta} to a counter. */
    public void addCounter(@NotNull String name, long delta) {
        counters.computeIfAbsent(name, n -> new LongAdder()).add(delta);
    }

    /** Get the current value of a counter (0 if never touched). */
    public long counter(@NotNull String name) {
        LongAdder adder = counters.get(name);
        return adder == null ? 0L : adder.sum();
    }

    /** Set a gauge to {@code value}. */
    public void setGauge(@NotNull String name, long value) {
        gauges.computeIfAbsent(name, n -> new AtomicLong()).set(value);
    }

    /** Get the current value of a gauge (0 if never set). */
    public long gauge(@NotNull String name) {
        AtomicLong holder = gauges.get(name);
        return holder == null ? 0L : holder.get();
    }

    /** Record a duration (nanoseconds) under a timer name. */
    public void recordTimer(@NotNull String name, long nanos) {
        timers.computeIfAbsent(name, n -> new TimerStats()).record(nanos);
    }

    /** Returns a TimerStats for inspection (or an empty one). */
    public @NotNull TimerStats timer(@NotNull String name) {
        return timers.computeIfAbsent(name, n -> new TimerStats());
    }

    /** Immutable snapshot of every counter. */
    public @NotNull Map<String, Long> snapshotCounters() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        counters.forEach((k, v) -> out.put(k, v.sum()));
        return Collections.unmodifiableMap(out);
    }

    /** Immutable snapshot of every gauge. */
    public @NotNull Map<String, Long> snapshotGauges() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        gauges.forEach((k, v) -> out.put(k, v.get()));
        return Collections.unmodifiableMap(out);
    }

    /** Immutable snapshot of every timer. */
    public @NotNull Map<String, TimerStats> snapshotTimers() {
        return Collections.unmodifiableMap(new java.util.LinkedHashMap<>(timers));
    }
}
