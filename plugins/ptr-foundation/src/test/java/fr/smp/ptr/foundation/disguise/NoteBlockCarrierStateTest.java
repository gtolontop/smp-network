package fr.smp.ptr.foundation.disguise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoteBlockCarrierStateTest {

    @Test
    void capacityIs800() {
        NoteBlockCarrier carrier = new NoteBlockCarrier();
        assertThat(carrier.capacity()).isEqualTo(800);
        assertThat(carrier.kind()).isEqualTo("note_block");
    }

    @Test
    void knownLeaksMentionCriticalSetting() {
        NoteBlockCarrier carrier = new NoteBlockCarrier();
        List<String> leaks = carrier.knownLeaks();
        assertThat(leaks).isNotEmpty();
        assertThat(String.join(" ", leaks))
                .contains("disable-noteblock-updates")
                .contains("NotePlayEvent");
    }

    @Test
    void mushroomCapacityIs192() {
        assertThat(new MushroomCarrier().capacity()).isEqualTo(192);
    }

    @Test
    void tripwireCapacityIs127() {
        assertThat(new TripwireCarrier().capacity()).isEqualTo(127);
    }

    @Test
    void displayBlockAndDisplayMobAreUnboundedInTheory() {
        assertThat(new DisplayBlockCarrier().capacity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(new DisplayMobCarrier().capacity()).isEqualTo(Integer.MAX_VALUE);
    }
}
