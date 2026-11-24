package io.github.grigoriev.im.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiTokenModelTest {

    private static final Instant AT = Instant.parse("2027-03-18T08:12:00Z");

    @Test
    void recordsAUseAndHidesTheHash() {
        ApiToken token = new ApiToken(1L, 2L, "Claude", "geheimer-hash", ApiTokenScope.READ, AT, AT.plusSeconds(9), null);
        assertThat(token.usedAt(AT.plusSeconds(5))).isEqualTo(new ApiToken(1L, 2L, "Claude", "geheimer-hash",
                ApiTokenScope.READ, AT, AT.plusSeconds(9), AT.plusSeconds(5)));
        assertThat(token.toString()).isEqualTo("ApiToken[id=1, userId=2, name=Claude, scope=READ]");
    }

    @Test
    void namesTheTokenInTheAuditLogAndTheScopeInGerman() {
        assertThat(new ApiTokenDetails(1L, "Claude Code").auditNote()).isEqualTo("API-Token „Claude Code“");
        assertThat(ApiTokenScope.READ.label()).isEqualTo("Nur lesen");
        assertThat(ApiTokenScope.WRITE.label()).isEqualTo("Lesen und schreiben");
    }
}
