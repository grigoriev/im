package io.github.grigoriev.im.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrityMonitorTest {

    private static final Instant NOW = Instant.parse("2027-05-12T01:30:00Z");

    private final IntegrityCheck invoices = mock(IntegrityCheck.class);
    private final IntegrityCheck documents = mock(IntegrityCheck.class);
    private final AuditService audit = mock(AuditService.class);
    private final IntegrityMonitor monitor = new IntegrityMonitor(List.of(invoices, documents), audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final Damage invoice = new Damage(5L, "Rechnung 2027-01", "/invoices/5", List.of("Die Datei fehlt."));
    private final Damage document = new Damage(null, "Dokument a.pdf", "/documents/2", List.of("Siegel."));

    @Test
    void knowsNothingBeforeTheFirstCheck() {
        assertThat(monitor.last()).isEmpty();
        verifyNoInteractions(invoices, documents, audit);
    }

    @Test
    void checksAtStartupAndEveryNightAndRecordsEachNewFindingOnce() {
        when(invoices.damaged()).thenReturn(List.of());
        when(documents.damaged()).thenReturn(List.of());
        monitor.atStartup();
        assertThat(monitor.last()).hasValueSatisfying(result -> {
            assertThat(result.checkedAt()).isEqualTo(NOW);
            assertThat(result.intact()).isTrue();
        });
        verifyNoInteractions(audit);

        when(invoices.damaged()).thenReturn(List.of(invoice));
        when(documents.damaged()).thenReturn(List.of(document));
        monitor.nightly();
        assertThat(monitor.last().orElseThrow().damaged()).containsExactly(invoice, document);
        verify(audit).add(5L, AuditAction.INTEGRITY_FAILED, "Rechnung 2027-01: Die Datei fehlt.");
        verify(audit).add(AuditAction.INTEGRITY_FAILED, "Dokument a.pdf: Siegel.");

        assertThat(monitor.check().damaged()).as("the same findings go into the log once").hasSize(2);
        verifyNoMoreInteractions(audit);

        Damage twin = new Damage(null, "Dokument a.pdf", "/documents/3", List.of("Siegel."));
        when(documents.damaged()).thenReturn(List.of(document, twin));
        monitor.check();
        verify(audit, times(2)).add(AuditAction.INTEGRITY_FAILED, "Dokument a.pdf: Siegel.");
    }
}
