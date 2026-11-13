package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class StaleFormHandlerTest {

    private final PersistentTokenRepository tokens = mock(PersistentTokenRepository.class);
    private final StaleFormHandler handler = new StaleFormHandler(tokens);

    private static String cookieValue(String plain) {
        return Base64.getEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequest logout(String contextPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", contextPath + "/logout");
        request.setContextPath(contextPath);
        return request;
    }

    @Test
    void keepsThe403PageForOtherDenials() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("nur Admins"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void sendsAnyOtherStaleFormToTheLoginPage() throws Exception {
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(login, response, new MissingCsrfTokenException("x"));
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?abgelaufen");
        MockHttpServletRequest getLogout = new MockHttpServletRequest("GET", "/logout");
        MockHttpServletResponse second = new MockHttpServletResponse();
        handler.handle(getLogout, second, new MissingCsrfTokenException("x"));
        assertThat(second.getRedirectedUrl()).isEqualTo("/login?abgelaufen");
    }

    @Test
    void aStaleLogoutEndsRememberMeCookieSessionAndContext() throws Exception {
        when(tokens.getTokenForSeries("serie")).thenReturn(new PersistentRememberMeToken("elena", "serie", "wert", new Date()));
        MockHttpServletRequest request = logout("/app");
        request.setSecure(true);
        request.setCookies(new Cookie("remember-me", cookieValue("serie:wert")));
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("elena", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response,
                new InvalidCsrfTokenException(new DefaultCsrfToken("X-CSRF", "_csrf", "a"), "b"));

        verify(tokens).removeUserTokens("elena");
        Cookie cleared = response.getCookie("remember-me");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getPath()).isEqualTo("/app");
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(cleared.getSecure()).isTrue();
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/app/login?abgemeldet");
    }

    @Test
    void aStaleLogoutWithoutCookieOrSessionStillLogsOut() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(logout(""), response, new MissingCsrfTokenException("x"));
        assertThat(response.getCookie("remember-me")).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?abgemeldet");
        verify(tokens, never()).removeUserTokens(any());
    }

    @Test
    void clearsAnUnknownOrBrokenCookieWithoutTouchingTheTokens() throws Exception {
        for (String value : new String[] {cookieValue("unbekannt:wert"), "%%%kein-base64", cookieValue("ohne-doppelpunkt"),
                cookieValue(":leer")}) {
            MockHttpServletRequest request = logout("");
            request.setCookies(new Cookie("remember-me", value));
            MockHttpServletResponse response = new MockHttpServletResponse();
            handler.handle(request, response, new MissingCsrfTokenException("x"));
            assertThat(response.getCookie("remember-me").getMaxAge()).isZero();
            assertThat(response.getCookie("remember-me").getPath()).isEqualTo("/");
        }
        verify(tokens, never()).removeUserTokens(any());
    }

    @Test
    void readsTheSeriesAsSpringSecurityWritesIt() {
        assertThat(StaleFormHandler.series(cookieValue("ab%2Bc:wert"))).isEqualTo("ab+c");
        assertThat(StaleFormHandler.series(cookieValue("serie12:wert"))).isEqualTo("serie12");
        assertThat(StaleFormHandler.series("@@@")).isNull();
    }
}
