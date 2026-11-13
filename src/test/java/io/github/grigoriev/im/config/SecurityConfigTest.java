package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void hashesWithArgon2() {
        PasswordEncoder encoder = config.passwordEncoder();
        String hash = encoder.encode("lang-genug-123");
        assertThat(hash).startsWith("{argon2}$argon2id$");
        assertThat(encoder.matches("lang-genug-123", hash)).isTrue();
        assertThat(encoder.matches("falsch", hash)).isFalse();
    }

    @Test
    void storesRememberMeTokensInTheDatabase() {
        assertThat(config.persistentTokenRepository(mock(DataSource.class))).isInstanceOf(JdbcTokenRepositoryImpl.class);
    }
}
