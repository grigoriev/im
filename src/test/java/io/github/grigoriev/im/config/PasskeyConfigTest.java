package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;

class PasskeyConfigTest {

    @Test
    void storesThePasskeysInTheDatabase() {
        PasskeyConfig config = new PasskeyConfig();
        JdbcOperations jdbc = mock(JdbcOperations.class);
        assertThat(config.passkeyUsers(jdbc)).isInstanceOf(JdbcPublicKeyCredentialUserEntityRepository.class);
        assertThat(config.passkeyCredentials(jdbc)).isInstanceOf(JdbcUserCredentialRepository.class);
    }

    @Test
    void holdsTheRelyingParty() {
        PasskeyProperties properties = new PasskeyProperties("im.h.g7v.io", "Rechnungen", List.of("https://im.h.g7v.io"));
        assertThat(properties.rpId()).isEqualTo("im.h.g7v.io");
        assertThat(properties.allowedOrigins()).containsExactly("https://im.h.g7v.io");
    }
}
