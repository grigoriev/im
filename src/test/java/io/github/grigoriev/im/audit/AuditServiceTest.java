package io.github.grigoriev.im.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:12:00Z");

    private final AuditRepository repository = mock(AuditRepository.class);
    private final AuditService service = new AuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearTheLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsTheLoggedInUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("elena", "x", "ROLE_USER"));
        service.add(AuditAction.CUSTOMER_CREATED, "Verlag am Fluss GmbH");
        verify(repository).save(new AuditEvent(null, NOW, "elena", AuditAction.CUSTOMER_CREATED, "Verlag am Fluss GmbH", null));
    }

    @Test
    void recordsTheAppItselfWithoutALogin() {
        service.add(AuditAction.USER_CREATED, "admin (ADMIN)");
        verify(repository).save(new AuditEvent(null, NOW, "system", AuditAction.USER_CREATED, "admin (ADMIN)", null));
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(AuditService.currentUser()).isEqualTo("system");
    }

    @Test
    void recordsAGivenUserAndCutsLongTexts() {
        service.add("u".repeat(70), AuditAction.LOGIN_FAILED, "d".repeat(2100));
        String user = "u".repeat(63) + "…";
        String detail = "d".repeat(1999) + "…";
        verify(repository).save(new AuditEvent(null, NOW, user, AuditAction.LOGIN_FAILED, detail, null));
        assertThat(user).hasSize(64);
        assertThat(detail).hasSize(2000);
    }

    @Test
    void keepsTextsUpToTheLimitAndTurnsNullIntoEmpty() {
        assertThat(AuditService.cut("x".repeat(2000), 2000)).hasSize(2000).doesNotContain("…");
        assertThat(AuditService.cut(null, 500)).isEmpty();
    }

    @Test
    void returnsTheNewestEntries() {
        List<AuditEvent> events = List.of(new AuditEvent(1L, NOW, "elena", AuditAction.LOGIN, "Passkey", null));
        when(repository.findTop200ByOrderByAtDescIdDesc()).thenReturn(events);
        assertThat(service.latest()).isEqualTo(events);
    }

    @Test
    void keepsTheHistoryOfAnInvoice() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("elena", "x", "ROLE_USER"));
        service.add(7L, AuditAction.INVOICE_ISSUED, "2027-01, Verlag am Fluss GmbH");
        verify(repository).save(new AuditEvent(null, NOW, "elena", AuditAction.INVOICE_ISSUED,
                "2027-01, Verlag am Fluss GmbH", 7L));
        List<AuditEvent> events = List.of(new AuditEvent(1L, NOW, "elena", AuditAction.DRAFT_CREATED, "Verlag", 7L));
        when(repository.findByInvoiceIdOrderByAtAscIdAsc(7L)).thenReturn(events);
        assertThat(service.history(7L)).isEqualTo(events);
    }

    @Test
    void labelsEveryActionInGerman() {
        assertThat(AuditAction.values()).allSatisfy(action -> assertThat(action.label()).isNotBlank());
        assertThat(AuditAction.LOGIN.label()).isEqualTo("Angemeldet");
        assertThat(AuditAction.CUSTOMER_DELETED.label()).isEqualTo("Kunde gelöscht");
    }
}
