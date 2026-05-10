package fr.smp.ptr.foundation.drop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DamageTrackerTest {

    @Test
    void accumulatesPerPlayer() {
        DamageTracker t = new DamageTracker();
        UUID boss = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        t.addDamage(boss, alice, 10.0);
        t.addDamage(boss, bob, 5.0);
        t.addDamage(boss, alice, 7.5);

        assertThat(t.damage(boss, alice)).isEqualTo(17.5);
        assertThat(t.damage(boss, bob)).isEqualTo(5.0);
        assertThat(t.totalDamage(boss)).isEqualTo(22.5);
    }

    @Test
    void leaderboardIsSortedDescending() {
        DamageTracker t = new DamageTracker();
        UUID boss = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        t.addDamage(boss, a, 5.0);
        t.addDamage(boss, b, 50.0);
        t.addDamage(boss, c, 25.0);

        var lb = t.leaderboard(boss);
        assertThat(lb).hasSize(3);
        assertThat(lb.get(0).player()).isEqualTo(b);
        assertThat(lb.get(0).damage()).isEqualTo(50.0);
        assertThat(lb.get(1).player()).isEqualTo(c);
        assertThat(lb.get(2).player()).isEqualTo(a);
    }

    @Test
    void emptyLeaderboardForUnknownBoss() {
        DamageTracker t = new DamageTracker();
        assertThat(t.leaderboard(UUID.randomUUID())).isEmpty();
        assertThat(t.totalDamage(UUID.randomUUID())).isEqualTo(0.0);
    }

    @Test
    void clearDropsTheBossEntry() {
        DamageTracker t = new DamageTracker();
        UUID boss = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        t.addDamage(boss, player, 10.0);
        t.clear(boss);
        assertThat(t.damage(boss, player)).isEqualTo(0.0);
    }
}
