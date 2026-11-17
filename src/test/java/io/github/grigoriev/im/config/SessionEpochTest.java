package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class SessionEpochTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");

    private final SessionEpoch epoch = new SessionEpoch(Clock.fixed(NOW, ZoneOffset.UTC));

    /** A session that started at the given moment. */
    private static MockHttpSession session(long createdAt) {
        return new MockHttpSession() {
            @Override
            public long getCreationTime() {
                return createdAt;
            }
        };
    }

    private MockHttpSession filter(MockHttpSession session) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockFilterChain chain = new MockFilterChain();
        epoch.filter().doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("the request goes on").isSameAs(request);
        return session;
    }

    @Test
    void everySessionCountsUntilTheFirstImport() throws Exception {
        assertThat(epoch.outdated(0)).isFalse();
        assertThat(filter(session(0)).isInvalid()).isFalse();
    }

    @Test
    void aRenewalEndsTheSessionsThatStartedBefore() throws Exception {
        epoch.renew();
        assertThat(epoch.outdated(NOW.toEpochMilli() - 1)).isTrue();
        assertThat(epoch.outdated(NOW.toEpochMilli())).as("a login in the same moment counts").isFalse();
        assertThat(filter(session(NOW.toEpochMilli() - 1)).isInvalid()).isTrue();
        assertThat(filter(session(NOW.toEpochMilli() + 1)).isInvalid()).isFalse();
        MockHttpServletRequest anonymous = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();
        epoch.filter().doFilter(anonymous, new MockHttpServletResponse(), chain);
        assertThat(anonymous.getSession(false)).as("no session is made").isNull();
        assertThat(chain.getRequest()).isSameAs(anonymous);
    }

    /** A session of a logged-in user that started at the given moment. */
    private static MockHttpSession sessionOf(String username, long createdAt) {
        MockHttpSession session = session(createdAt);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new TestingAuthenticationToken(username, null, "ROLE_USER")));
        return session;
    }

    @Test
    void aDeletedUserLosesTheOwnSessionsOnly() throws Exception {
        long before = NOW.toEpochMilli() - 1;
        epoch.end("admin");
        assertThat(epoch.ended("admin", before)).isTrue();
        assertThat(epoch.ended("admin", NOW.toEpochMilli())).as("a session of the same millisecond ends too").isTrue();
        assertThat(epoch.ended("admin", NOW.toEpochMilli() + 1)).as("a later login counts").isFalse();
        assertThat(epoch.ended("elena", before)).isFalse();
        assertThat(filter(sessionOf("admin", before)).isInvalid()).isTrue();
        assertThat(filter(sessionOf("admin", NOW.toEpochMilli() + 1)).isInvalid()).isFalse();
        assertThat(filter(sessionOf("elena", before)).isInvalid()).isFalse();
        assertThat(filter(session(before)).isInvalid()).as("no login in the session").isFalse();
        MockHttpSession empty = session(before);
        empty.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl());
        assertThat(filter(empty).isInvalid()).as("a context without a login").isFalse();
    }

    @Test
    void aNewPasswordEndsTheOtherSessionsOfItsUser() throws Exception {
        long before = NOW.toEpochMilli() - 1;
        MockHttpSession current = sessionOf("elena", before);
        epoch.endOthers("elena", current);
        assertThat(filter(current).isInvalid()).as("the session of the change stays").isFalse();
        assertThat(filter(sessionOf("elena", before)).isInvalid()).as("another device").isTrue();
        assertThat(filter(sessionOf("elena", NOW.toEpochMilli())).isInvalid()).isTrue();
        assertThat(filter(sessionOf("elena", NOW.toEpochMilli() + 1)).isInvalid()).as("a later login").isFalse();
        assertThat(filter(sessionOf("admin", before)).isInvalid()).isFalse();
    }

    @Test
    void aLaterEndTakesTheKeptSessionToo() throws Exception {
        MockHttpSession current = sessionOf("elena", NOW.toEpochMilli() - 1);
        epoch.endOthers("elena", current);
        SessionEpoch later = new SessionEpoch(Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        later.endOthers("elena", sessionOf("elena", NOW.toEpochMilli()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(current);
        later.filter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(current.isInvalid()).as("the session kept by an earlier change").isTrue();
    }
}
