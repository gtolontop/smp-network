package fr.smp.ptr.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PtrServicesTest {

    interface Foo {
        String hello();
    }

    interface Bar {
        int value();
    }

    @Test
    void registersAndLooksUp() {
        PtrServices s = new PtrServices();
        Foo foo = () -> "hi";
        s.register(Foo.class, foo);
        assertThat(s.get(Foo.class).hello()).isEqualTo("hi");
        assertThat(s.find(Foo.class)).isPresent();
        assertThat(s.find(Bar.class)).isEmpty();
    }

    @Test
    void duplicateRegistrationFails() {
        PtrServices s = new PtrServices();
        s.register(Foo.class, () -> "a");
        assertThatThrownBy(() -> s.register(Foo.class, () -> "b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getMissingThrows() {
        PtrServices s = new PtrServices();
        assertThatThrownBy(() -> s.get(Foo.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shutdownAllInvokesLifo() {
        PtrServices s = new PtrServices();
        List<String> order = new ArrayList<>();
        s.register(Foo.class, () -> "1", impl -> order.add("foo"));
        s.register(Bar.class, () -> 1, impl -> order.add("bar"));

        s.shutdownAll();

        assertThat(order).containsExactly("bar", "foo");
        assertThat(s.size()).isEqualTo(0);
    }
}
