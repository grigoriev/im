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
}
