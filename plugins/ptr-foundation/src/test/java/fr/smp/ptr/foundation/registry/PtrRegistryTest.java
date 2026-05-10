package fr.smp.ptr.foundation.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class PtrRegistryTest {

    @Test
    void registersAndIteratesInInsertionOrder() {
        PtrBlockRegistry reg = new PtrBlockRegistry();
        PtrBlockDef a = PtrBlockDef.builder().id("alpha").displayName(Component.text("A")).build();
        PtrBlockDef b = PtrBlockDef.builder().id("bravo").displayName(Component.text("B")).build();
        PtrBlockDef c = PtrBlockDef.builder().id("charlie").displayName(Component.text("C")).build();

        reg.register(a);
        reg.register(b);
        reg.register(c);

        assertThat(reg.size()).isEqualTo(3);
        List<NamespacedKey> keys = new ArrayList<>(reg.view().keySet());
        assertThat(keys).containsExactly(a.id(), b.id(), c.id());
    }

    @Test
    void rejectsDuplicates() {
        PtrBlockRegistry reg = new PtrBlockRegistry();
        PtrBlockDef def =
                PtrBlockDef.builder().id("dup").displayName(Component.text("dup")).build();
        reg.register(def);

        assertThatThrownBy(() -> reg.register(def)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void viewIsImmutable() {
        PtrBlockRegistry reg = new PtrBlockRegistry();
        PtrBlockDef def =
                PtrBlockDef.builder().id("im").displayName(Component.text("im")).build();
        reg.register(def);

        var view = reg.view();
        assertThatThrownBy(() -> view.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(view).hasSize(1);
        // mutating the underlying registry doesn't reflect in the snapshot
        reg.register(
                PtrBlockDef.builder().id("im2").displayName(Component.text("im2")).build());
        assertThat(view).hasSize(1);
        assertThat(reg.view()).hasSize(2);
    }

    @Test
    void getReturnsEmptyForMissingId() {
        PtrBlockRegistry reg = new PtrBlockRegistry();
        assertThat(reg.get(PtrIds.key("ghost"))).isEmpty();
    }
}
