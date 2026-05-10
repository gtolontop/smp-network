package fr.smp.ptr.foundation.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PtrMetricsTest {

    @Test
    void counterStartsAtZero() {
        PtrMetrics m = new PtrMetrics();
        assertThat(m.counter("unseen")).isEqualTo(0L);
    }

    @Test
    void counterIncrementsAndAdds() {
        PtrMetrics m = new PtrMetrics();
        m.incrementCounter("hits");
        m.incrementCounter("hits");
        m.addCounter("hits", 5);
        assertThat(m.counter("hits")).isEqualTo(7L);
    }

    @Test
    void gaugeRoundTrips() {
        PtrMetrics m = new PtrMetrics();
        m.setGauge("players", 42);
        assertThat(m.gauge("players")).isEqualTo(42L);
        m.setGauge("players", 17);
        assertThat(m.gauge("players")).isEqualTo(17L);
    }

    @Test
    void timerTracksCountTotalAndMax() {
        PtrMetrics m = new PtrMetrics();
        m.recordTimer("io", 100);
        m.recordTimer("io", 300);
        m.recordTimer("io", 200);

        var stats = m.timer("io");
        assertThat(stats.count()).isEqualTo(3L);
        assertThat(stats.totalNanos()).isEqualTo(600L);
        assertThat(stats.maxNanos()).isEqualTo(300L);
        assertThat(stats.avgNanos()).isEqualTo(200L);
    }

    @Test
    void snapshotsAreImmutable() {
        PtrMetrics m = new PtrMetrics();
        m.incrementCounter("c");
        var counters = m.snapshotCounters();
        org.assertj.core.api.Assertions.assertThatThrownBy(counters::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
