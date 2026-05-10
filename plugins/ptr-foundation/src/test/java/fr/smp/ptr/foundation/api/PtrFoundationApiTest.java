package fr.smp.ptr.foundation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.smp.ptr.foundation.PtrServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PtrFoundationApiTest {

    @AfterEach
    void clean() {
        PtrFoundationApi.shutdown();
    }

    @Test
    void notReadyBeforeInit() {
        assertThat(PtrFoundationApi.isReady()).isFalse();
        assertThatThrownBy(PtrFoundationApi::services)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void initAndShutdownRoundTrip() {
        PtrServices services = new PtrServices();
        PtrFoundationApi.init(services);
        assertThat(PtrFoundationApi.isReady()).isTrue();
        assertThat(PtrFoundationApi.services()).isSameAs(services);

        PtrFoundationApi.shutdown();
        assertThat(PtrFoundationApi.isReady()).isFalse();
    }

    @Test
    void doubleInitFails() {
        PtrFoundationApi.init(new PtrServices());
        assertThatThrownBy(() -> PtrFoundationApi.init(new PtrServices()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void servicesOptIsEmptyBeforeInit() {
        assertThat(PtrFoundationApi.servicesOpt()).isEmpty();
    }
}
