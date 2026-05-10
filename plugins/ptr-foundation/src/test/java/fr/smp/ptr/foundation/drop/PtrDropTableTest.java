package fr.smp.ptr.foundation.drop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/**
 * Structural tests for {@link PtrDropTable}'s builder.
 *
 * <p>Roll-output and per-drop-validation tests would require constructing
 * real {@link org.bukkit.inventory.ItemStack}s, which is impossible in
 * this JUnit-only environment — paper-api's {@code Registry} static
 * initialiser needs a live server and {@code ItemStack} is not mockable
 * by Mockito-inline on JDK 25. Coverage of those code paths happens at
 * smoke-test boot time once content layers register real drop tables.
 */
class PtrDropTableTest {

    @Test
    void builderRequiresIdAndDisplayName() {
        assertThatThrownBy(() -> PtrDropTable.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> PtrDropTable.builder().id("x").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void builderRejectsNonPositiveRank() {
        assertThatThrownBy(
                        () ->
                                PtrDropTable.builder()
                                        .id("x")
                                        .displayName(Component.text("x"))
                                        .addRankDrop(0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyTableIsBuildable() {
        PtrDropTable t =
                PtrDropTable.builder()
                        .id("empty")
                        .displayName(Component.text("Empty"))
                        .build();
        assertThat(t.id().toString()).isEqualTo("ptr:empty");
        assertThat(t.displayName()).isEqualTo(Component.text("Empty"));
    }

    @Test
    void recordsRejectNullStack() {
        // Records validate via Objects.requireNonNull — null stack throws NPE.
        assertThatThrownBy(() -> new PtrDropTable.RandomDrop(null, 0.5))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PtrDropTable.ParticipationDrop(null, 0.1))
                .isInstanceOf(NullPointerException.class);
    }
}
