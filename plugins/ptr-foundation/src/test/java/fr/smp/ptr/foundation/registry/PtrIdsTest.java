package fr.smp.ptr.foundation.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PtrIdsTest {

    @Test
    void keyForcesPtrNamespace() {
        assertThat(PtrIds.key("my_block").toString()).isEqualTo("ptr:my_block");
    }

    @Test
    void parseAcceptsPtrPrefixedKeys() {
        assertThat(PtrIds.parse("ptr:foo").getKey()).isEqualTo("foo");
    }

    @Test
    void parseRejectsForeignNamespaces() {
        assertThatThrownBy(() -> PtrIds.parse("minecraft:stone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ptr");
    }

    @Test
    void parseRejectsInvalidStrings() {
        assertThatThrownBy(() -> PtrIds.parse("not a key"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
