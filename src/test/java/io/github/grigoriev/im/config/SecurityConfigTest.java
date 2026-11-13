package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
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
    void sendsAStaleFormToTheLoginPageAndOtherDenialsToThe403Page() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/app");
        MockHttpServletResponse stale = new MockHttpServletResponse();
        SecurityConfig.accessDenied(request, stale,
                new InvalidCsrfTokenException(new DefaultCsrfToken("X-CSRF", "_csrf", "a"), "b"));
        assertThat(stale.getRedirectedUrl()).isEqualTo("/app/login?abgelaufen");
        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        SecurityConfig.accessDenied(new MockHttpServletRequest(), forbidden, new AccessDeniedException("no"));
        assertThat(forbidden.getStatus()).isEqualTo(403);
    }

    @Test
    void storesRememberMeTokensInTheDatabase() {
        assertThat(config.persistentTokenRepository(mock(DataSource.class))).isInstanceOf(JdbcTokenRepositoryImpl.class);
    }
}
