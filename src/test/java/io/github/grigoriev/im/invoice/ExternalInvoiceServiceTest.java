package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.ItSamples;
import io.github.grigoriev.im.numbering.ManualNumbers;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SettingsException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalInvoiceServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 3, 18);
    private static final byte[] XLSX = SampleOffice.workbook();

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final IssueService issues = mock(IssueService.class);
    private final NumberService numbers = mock(NumberService.class);
    private final ManualNumbers manual = mock(ManualNumbers.class);
    private final OriginalStore originals = mock(OriginalStore.class);
    private final AuditService audit = mock(AuditService.class);
    private final ExternalInvoiceService service = new ExternalInvoiceService(invoices, issues, numbers, manual,
            originals, audit, Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    private final Invoice draft = invoice(InvoiceStatus.DRAFT, InvoiceFormat.EXTERNAL, null);
    private final InvoiceDocument document = ItSamples.reading(InvoiceFormat.EXTERNAL);
    private final InvoiceOriginal stored = new InvoiceOriginal(9, 5, "Rechnung März.xlsx", OriginalStore.XLSX, XLSX,
            "abc", XLSX.length, "seal", NOW, "elena");

    private static Invoice invoice(InvoiceStatus status, InvoiceFormat format, String number) {
        return new Invoice(5L, status, 2L, number, format, DAY, DAY, DAY, 14, null, null, null, null, List.of(), NOW,
                NOW, 3L, status == InvoiceStatus.DRAFT ? null : NOW, status == InvoiceStatus.DRAFT ? null : "{}", null);
    }

    @BeforeEach
    void complete() {
        when(invoices.draft(5)).thenReturn(draft);
        when(invoices.problems(draft)).thenReturn(List.of());
        when(numbers.assign(DAY)).thenReturn(Optional.of("2027-10"));
        when(invoices.document(draft, "2027-10")).thenReturn(Optional.of(document));
        when(originals.store(5, "2027-10", "Rechnung März.xlsx", XLSX, OriginalStore.XLSX, NOW)).thenReturn(stored);
        when(manual.claim(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void takesTheNumberOfTheFileInAYearByHand() {
        when(manual.claim(DAY, "2026-07")).thenReturn(Optional.of("2026-07"));
        when(invoices.document(draft, "2026-07")).thenReturn(Optional.of(document));
        when(originals.store(5, "2026-07", "Rechnung März.xlsx", XLSX, OriginalStore.XLSX, NOW)).thenReturn(stored);
        assertThat(service.register(5, 3L, "2026-07", "Rechnung März.xlsx", XLSX)).isEqualTo("2026-07");
        verify(numbers, never()).assign(any());
        verify(issues).freeze(draft, document, NOW);

        when(manual.claim(DAY, "2026-01")).thenThrow(new SettingsException(List.of("Die Nummer 2026-01 gibt es schon.")));
        assertThatThrownBy(() -> service.register(5, 3L, "2026-01", "Rechnung März.xlsx", XLSX))
                .isInstanceOf(InvoiceException.class).hasMessage("Die Nummer 2026-01 gibt es schon.");
    }

    @Test
    void refusesAYearThatTurnedToNumbersByHandMeanwhile() {
        when(numbers.assign(DAY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register(5, 3L, "2027-10", "Rechnung März.xlsx", XLSX))
                .isInstanceOf(InvoiceException.class).hasMessage(IssueService.BY_HAND.formatted(2026));
        verifyNoInteractions(issues, originals, audit);
    }

    @Test
    void showsTheNextNumberOfTheYearOfTheInvoice() {
        when(numbers.next(2026)).thenReturn("2026-04");
        assertThat(service.nextNumber(draft)).contains("2026-04");
        when(manual.isManual(2026)).thenReturn(true);
        assertThat(service.nextNumber(draft)).as("the file names the number of a year by hand").isEmpty();
    }

    @Test
    void registersAnExternalInvoiceWithItsNumberAndFile() {
        assertThat(service.register(5, 3L, " 2027-10 ", "Rechnung März.xlsx", XLSX)).isEqualTo("2027-10");
        verify(issues).freeze(draft, document, NOW);
        verify(originals).store(5, "2027-10", "Rechnung März.xlsx", XLSX, OriginalStore.XLSX, NOW);
        verify(audit).add(5L, AuditAction.INVOICE_REGISTERED,
                "2027-10, Verlag am Fluss GmbH, Rechnung März.xlsx (SHA-256 abc)");
    }

    @Test
    void refusesAFileWithAnotherNumberAndKeepsNothing() {
        assertThatThrownBy(() -> service.register(5, 3L, "2027-11", "Rechnung März.xlsx", XLSX))
                .isInstanceOf(InvoiceException.class)
                .hasMessage("Die nächste Rechnungsnummer ist 2027-10, angegeben ist 2027-11. Bitte die Nummer in der "
                        + "Datei korrigieren. Es wurde nichts gespeichert.");
        verifyNoInteractions(issues, originals, audit);
    }

    @Test
    void refusesAnOldVersionADraftOfTheAppAndMissingValues() {
        assertThatThrownBy(() -> service.register(5, 2L, "2027-10", "a.xlsx", XLSX))
                .hasMessage(InvoiceService.CHANGED_ELSEWHERE);
        assertThatThrownBy(() -> service.register(5, null, "2027-10", "a.xlsx", XLSX))
                .hasMessage(InvoiceService.CHANGED_ELSEWHERE);

        when(invoices.problems(draft)).thenReturn(List.of("Den Kunden gibt es nicht mehr."));
        assertThatThrownBy(() -> service.register(5, 3L, null, "a.txt", "Text".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("Den Kunden gibt es nicht mehr. " + ExternalInvoiceService.NUMBER_MISSING + " "
                        + OriginalStore.WRONG_TYPE);
        assertThatThrownBy(() -> service.register(5, 3L, "  ", "a.xlsx", XLSX))
                .hasMessage("Den Kunden gibt es nicht mehr. " + ExternalInvoiceService.NUMBER_MISSING);

        Invoice ofTheApp = invoice(InvoiceStatus.DRAFT, InvoiceFormat.ZUGFERD_EN16931, null);
        when(invoices.draft(5)).thenReturn(ofTheApp);
        assertThatThrownBy(() -> service.register(5, 3L, "2027-10", "a.xlsx", XLSX))
                .hasMessage(ExternalInvoiceService.NOT_EXTERNAL);
        verify(numbers, never()).assign(any());
        verifyNoInteractions(issues, originals, audit);
    }

    @Test
    void addsAFurtherFileToARegisteredExternalInvoice() {
        Invoice registered = invoice(InvoiceStatus.ISSUED, InvoiceFormat.EXTERNAL, "2026-07");
        when(invoices.find(5)).thenReturn(Optional.of(registered));
        InvoiceOriginal pdf = new InvoiceOriginal(10, 5, "Rechnung.pdf", OriginalStore.PDF, new byte[] {1}, "def", 1,
                "seal", NOW, "elena");
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        when(originals.store(5, "2026-07", "Rechnung.pdf", content, OriginalStore.PDF, NOW)).thenReturn(pdf);
        assertThat(service.addOriginal(5, "Rechnung.pdf", content)).isEqualTo(pdf);
        verify(audit).add(5L, AuditAction.ORIGINAL_ADDED, "Rechnung.pdf (SHA-256 def)");
    }

    @Test
    void addsNoFileToADraftAnInvoiceOfTheAppOrAWrongType() {
        when(invoices.find(4)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addOriginal(4, "a.pdf", new byte[] {1}))
                .hasMessage(InvoiceService.UNKNOWN);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.addOriginal(5, "a.pdf", new byte[] {1}))
                .hasMessage(ExternalInvoiceService.NO_ORIGINALS);
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.ISSUED, InvoiceFormat.XRECHNUNG,
                "2027-01")));
        assertThatThrownBy(() -> service.addOriginal(5, "a.pdf", new byte[] {1}))
                .hasMessage(ExternalInvoiceService.NO_ORIGINALS);
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.SENT, InvoiceFormat.EXTERNAL,
                "2026-07")));
        assertThatThrownBy(() -> service.addOriginal(5, "a.txt", new byte[DocumentService.MAX_BYTES + 1]))
                .hasMessage(DocumentService.TOO_LARGE);
        verify(originals, never()).store(anyLong(), any(), any(), any(), any(), any());
        verifyNoInteractions(audit);
    }
}
