package io.github.grigoriev.im.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.system.AppVersion;
import io.github.grigoriev.im.system.SystemService;
import org.junit.jupiter.api.Test;

class VersionAdviceTest {

    @Test
    void givesEveryPageTheVersion() {
        SystemService system = mock(SystemService.class);
        AppVersion version = new AppVersion("0.1.12", null);
        when(system.version()).thenReturn(version);
        assertThat(new VersionAdvice(system).appVersion()).isSameAs(version);
    }
}
