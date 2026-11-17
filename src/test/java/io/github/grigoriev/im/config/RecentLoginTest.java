package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RecentLoginTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");

    private final RecentLogin recent = new RecentLogin(Clock.fixed(NOW, ZoneOffset.UTC));

    private static Authentication login(String factor, Instant at) {
        return new TestingAuthenticationToken("elena", null,
                List.of(FactorGrantedAuthority.withAuthority(factor).issuedAt(at).build()));
    }

    @Test
    void aLoginWithThePasswordOrAPasskeyCountsForFifteenMinutes() {
        Instant edge = NOW.minus(RecentLogin.VALID);
        assertThat(recent.holds(login(FactorGrantedAuthority.PASSWORD_AUTHORITY, edge), null)).isTrue();
        assertThat(recent.holds(login(FactorGrantedAuthority.WEBAUTHN_AUTHORITY, edge), null)).isTrue();
        assertThat(recent.holds(login(FactorGrantedAuthority.PASSWORD_AUTHORITY, edge.minusMillis(1)), null))
                .as("an older login").isFalse();
    }

    @Test
    void angemeldetBleibenAndNoLoginDoNotCount() {
        assertThat(recent.holds(new RememberMeAuthenticationToken("key", "elena",
                AuthorityUtils.createAuthorityList("ROLE_USER")), null)).isFalse();
        assertThat(recent.holds(new TestingAuthenticationToken("elena", null, "ROLE_USER"), new MockHttpSession()))
                .isFalse();
        assertThat(recent.holds(null, null)).isFalse();
    }

    @Test
    void aConfirmedPasswordCountsForFifteenMinutesInItsSession() {
        Authentication remembered = new TestingAuthenticationToken("elena", null, "ROLE_USER");
        MockHttpSession session = new MockHttpSession();
        recent.confirm(session);
        assertThat(session.getAttribute(RecentLogin.CONFIRMED)).isEqualTo(NOW);
        assertThat(recent.holds(remembered, session)).isTrue();
        session.setAttribute(RecentLogin.CONFIRMED, NOW.minus(RecentLogin.VALID));
        assertThat(recent.holds(remembered, session)).isTrue();
        session.setAttribute(RecentLogin.CONFIRMED, NOW.minus(RecentLogin.VALID).minusMillis(1));
        assertThat(recent.holds(remembered, session)).as("an older confirmation").isFalse();
        session.setAttribute(RecentLogin.CONFIRMED, "now");
        assertThat(recent.holds(remembered, session)).isFalse();
    }

    private MockHttpServletResponse register(String path, Authentication authentication, MockHttpSession session)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        try {
            recent.filter().doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }
        if (response.getStatus() == 200) {
            assertThat(chain.getRequest()).as("the request goes on").isSameAs(request);
        } else {
            assertThat(chain.getRequest()).as("the request stops").isNull();
        }
        return response;
    }

    @Test
    void theFilterRefusesTheRegistrationWithoutARecentLogin() throws Exception {
        Authentication remembered = new TestingAuthenticationToken("elena", null, "ROLE_USER");
        assertThat(register("/webauthn/register/options", remembered, null).getStatus()).isEqualTo(403);
        assertThat(register("/webauthn/register", remembered, new MockHttpSession()).getStatus()).isEqualTo(403);
        assertThat(register("/webauthn/%72egister/options", remembered, null).getStatus()).as("an encoded letter")
                .isEqualTo(403);
        assertThat(register("/webauthn/%72egister", remembered, null).getStatus()).isEqualTo(403);
        assertThat(register("/webauthn/registered", remembered, null).getStatus()).as("another path").isEqualTo(200);
        assertThat(register("/account", remembered, null).getStatus()).isEqualTo(200);
        assertThat(register("/webauthn/register/options",
                login(FactorGrantedAuthority.PASSWORD_AUTHORITY, NOW), null).getStatus()).isEqualTo(200);
        MockHttpSession session = new MockHttpSession();
        recent.confirm(session);
        assertThat(register("/webauthn/register", remembered, session).getStatus()).isEqualTo(200);
    }
}
