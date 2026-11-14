package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.EInvoiceException;
import io.github.grigoriev.im.einvoice.EInvoiceService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.IssuedEInvoice;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import io.github.grigoriev.im.numbering.NumberService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class IssueServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 18);

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final NumberService numbers = mock(NumberService.class);
    private final EInvoiceService einvoices = mock(EInvoiceService.class);
    private final DocumentStore store = mock(DocumentStore.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec statement = mock(StatementSpec.class);
    private final AuditService audit = mock(AuditService.class);
    private final IssueService service = new IssueService(invoices, numbers, einvoices, store, jdbc, audit,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    private final Invoice draft = new Invoice(5L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_EN16931, TODAY,
            TODAY, TODAY, 14, null, null, null, null, List.of(), NOW, NOW, 3L, null, null);
    private final InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
    private final IssuedEInvoice files = new IssuedEInvoice(InvoiceFormat.ZUGFERD_EN16931, new byte[] {1},
            new byte[] {2}, List.of());

    @BeforeEach
    void complete() {
        when(invoices.draft(5)).thenReturn(draft);
        when(invoices.problems(draft)).thenReturn(List.of());
        when(numbers.assign(TODAY)).thenReturn("2027-10");
        when(invoices.document(draft, "2027-10")).thenReturn(Optional.of(document));
        when(einvoices.issue(document)).thenReturn(files);
        when(store.snapshot(document)).thenReturn("{}");
        when(jdbc.sql(IssueService.ISSUE)).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.update()).thenReturn(1);
    }

    @Test
    void aStornorechnungCancelsItsInvoice() {
        Invoice original = new Invoice(7L, InvoiceStatus.PAID, 2L, "2027-07", InvoiceFormat.ZUGFERD_EN16931, TODAY,
                TODAY, TODAY, 14, null, null, null, null, List.of(), NOW, NOW, 3L, NOW, "{}");
        when(invoices.cancelled(draft)).thenReturn(Optional.of(original));
        StatementSpec cancel = mock(StatementSpec.class);
        when(jdbc.sql(IssueService.CANCEL)).thenReturn(cancel);
        when(cancel.param(anyString(), any())).thenReturn(cancel);
        when(cancel.update()).thenReturn(1);
        assertThat(service.issue(5, 3L)).isEqualTo("2027-10");
        verify(cancel).param("at", Timestamp.from(NOW));
        verify(cancel).param("id", 7L);
        verify(audit).add(7L, AuditAction.INVOICE_CANCELLED, "2027-07, durch Stornorechnung 2027-10");
        verify(store).store(5, "2027-10", files, NOW);

        when(cancel.update()).thenReturn(0);
        assertThatThrownBy(() -> service.issue(5, 3L)).isInstanceOf(InvoiceException.class)
                .hasMessage("Die Rechnung 2027-07 ist schon storniert. Die Stornorechnung bleibt ein Entwurf.");
        verify(store).store(5, "2027-10", files, NOW);
    }

    @Test
    void issuesACompleteDraft() {
        assertThat(service.issue(5, 3L)).isEqualTo("2027-10");
        verify(statement).param("number", "2027-10");
        verify(statement).param("at", Timestamp.from(NOW));
        verify(statement).param("snapshot", "{}");
        verify(statement).param("id", 5L);
        verify(statement).param("version", 3L);
        verify(store).store(5, "2027-10", files, NOW);
        verify(audit).add(5L, AuditAction.INVOICE_ISSUED, "2027-10, Verlag am Fluss GmbH");
        verify(jdbc, never()).sql(IssueService.CANCEL);
    }

    @Test
    void refusesAnOldOrMissingVersion() {
        assertThatThrownBy(() -> service.issue(5, 2L)).isInstanceOf(InvoiceException.class)
                .hasMessage(InvoiceService.CHANGED_ELSEWHERE);
        assertThatThrownBy(() -> service.issue(5, null)).isInstanceOf(InvoiceException.class)
                .hasMessage(InvoiceService.CHANGED_ELSEWHERE);
        verifyNoInteractions(numbers, einvoices, store, audit);
    }

    @Test
    void refusesAnIncompleteDraftBeforeItTakesANumber() {
        when(invoices.problems(draft)).thenReturn(List.of("Eins.", "Zwei."));
        assertThatThrownBy(() -> service.issue(5, 3L)).isInstanceOf(InvoiceException.class)
                .hasMessage("Eins. Zwei.");
        verifyNoInteractions(numbers, einvoices, store, audit);
    }

    @Test
    void keepsTheDraftIfTheValidationFails() {
        when(einvoices.issue(document)).thenThrow(new EInvoiceException("KoSIT said no"));
        assertThatThrownBy(() -> service.issue(5, 3L)).isInstanceOf(InvoiceException.class)
                .hasMessage(IssueService.NOT_VALID);
        verifyNoInteractions(jdbc, store, audit);
    }

    @Test
    void namesADuplicateNumber() {
        when(statement.update()).thenThrow(new DuplicateKeyException("invoice_number_key"));
        assertThatThrownBy(() -> service.issue(5, 3L)).isInstanceOf(InvoiceException.class)
                .hasMessage("Die Rechnungsnummer 2027-10 gibt es schon. Bitte das Nummernmuster oder die Startnummer in "
                        + "den Einstellungen prüfen.");
        verify(store, never()).store(any(Long.class), any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void refusesADraftThatChangedMeanwhile() {
        when(statement.update()).thenReturn(0);
        assertThatThrownBy(() -> service.issue(5, 3L)).isInstanceOf(InvoiceException.class)
                .hasMessage(InvoiceService.CHANGED_ELSEWHERE);
        verify(store, never()).store(any(Long.class), any(), any(), any());
        verifyNoInteractions(audit);
    }
}
