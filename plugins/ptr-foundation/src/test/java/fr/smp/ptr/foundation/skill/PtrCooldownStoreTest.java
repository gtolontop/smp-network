package fr.smp.ptr.foundation.skill;

import static org.assertj.core.api.Assertions.assertThat;

import fr.smp.ptr.foundation.registry.PtrIds;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class PtrCooldownStoreTest {

    @Test
    void zeroCooldownIsAlwaysAvailable() {
        PtrCooldownStore store = new PtrCooldownStore();
        NamespacedKey skill = PtrIds.key("smash");
        UUID caster = UUID.randomUUID();
        assertThat(store.tryAcquire(skill, caster, 0L)).isTrue();
        assertThat(store.tryAcquire(skill, caster, 0L)).isTrue();
    }

    @Test
    void blocksWithinCooldownWindow() {
        PtrCooldownStore store = new PtrCooldownStore();
        NamespacedKey skill = PtrIds.key("smash");
        UUID caster = UUID.randomUUID();

        assertThat(store.tryAcquire(skill, caster, 10_000L)).isTrue();
        assertThat(store.tryAcquire(skill, caster, 10_000L)).isFalse();
    }

    @Test
    void perCasterCooldownsAreIndependent() {
        PtrCooldownStore store = new PtrCooldownStore();
        NamespacedKey skill = PtrIds.key("smash");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(store.tryAcquire(skill, a, 10_000L)).isTrue();
        assertThat(store.tryAcquire(skill, b, 10_000L)).isTrue();
        assertThat(store.tryAcquire(skill, a, 10_000L)).isFalse();
    }

    @Test
    void resetReleasesCooldown() {
        PtrCooldownStore store = new PtrCooldownStore();
        NamespacedKey skill = PtrIds.key("smash");
        UUID caster = UUID.randomUUID();

        store.tryAcquire(skill, caster, 60_000L);
        assertThat(store.tryAcquire(skill, caster, 60_000L)).isFalse();

        store.reset(skill, caster);
        assertThat(store.tryAcquire(skill, caster, 60_000L)).isTrue();
    }

    @Test
    void resetAllClearsEveryEntryForCaster() {
        PtrCooldownStore store = new PtrCooldownStore();
        NamespacedKey s1 = PtrIds.key("a");
        NamespacedKey s2 = PtrIds.key("b");
        UUID caster = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        store.tryAcquire(s1, caster, 60_000L);
        store.tryAcquire(s2, caster, 60_000L);
        store.tryAcquire(s1, other, 60_000L);

        store.resetAll(caster);

        assertThat(store.tryAcquire(s1, caster, 60_000L)).isTrue();
        assertThat(store.tryAcquire(s2, caster, 60_000L)).isTrue();
        assertThat(store.tryAcquire(s1, other, 60_000L)).isFalse();
    }
}
