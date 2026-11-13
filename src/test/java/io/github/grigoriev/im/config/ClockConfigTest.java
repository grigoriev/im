package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void usesTheGermanTimeZone() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
    }
}
