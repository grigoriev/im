package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ArchiveServiceTest {

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final DocumentStore documents = mock(DocumentStore.class);
    private final ArchiveService service = new ArchiveService(invoices, documents);

    private static Invoice invoice(long id, InvoiceStatus status, String number, LocalDate date) {
        boolean draft = status == InvoiceStatus.DRAFT;
        return new Invoice(id, status, 2L, number, InvoiceFormat.ZUGFERD_EN16931, date, date, date, 14, null, null, null,
                null, List.of(), Instant.EPOCH, Instant.EPOCH, 1L, draft ? null : Instant.EPOCH, draft ? null : "{}");
    }

    private static StoredDocument file(long invoiceId, DocumentKind kind, String number, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new StoredDocument(invoiceId * 10, invoiceId, kind, kind.filename(number), bytes, "sha-" + number,
                bytes.length, Instant.EPOCH);
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void zipsTheDocumentsOfTheIssuedInvoicesOfAYearWithTheirContents() throws IOException {
        Invoice march = invoice(2, InvoiceStatus.PAID, "2027-02", LocalDate.of(2027, 3, 1));
        Invoice january = invoice(1, InvoiceStatus.CANCELLED, "2027-01", LocalDate.of(2027, 1, 10));
        Invoice sameDay = invoice(3, InvoiceStatus.ISSUED, "2027-03", LocalDate.of(2027, 3, 1));
        when(invoices.list()).thenReturn(List.of(sameDay, march, invoice(4, InvoiceStatus.DRAFT, null,
                LocalDate.of(2027, 5, 1)), january, invoice(5, InvoiceStatus.SENT, "2026-09", LocalDate.of(2026, 12, 31))));
        when(documents.restore("{}")).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(documents.list(1)).thenReturn(List.of(file(1, DocumentKind.PDF, "2027-01", "%PDF-1"),
                file(1, DocumentKind.XML, "2027-01", "<x>1</x>")));
        when(documents.list(2)).thenReturn(List.of(file(2, DocumentKind.PDF, "2027-02", "%PDF-2")));
        when(documents.list(3)).thenReturn(List.of(file(3, DocumentKind.XML, "2027-03", "<x>3</x>")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(2027, out);
        Map<String, String> entries = unzip(out.toByteArray());
        assertThat(entries.keySet()).as("by invoice date, then ID; drafts and other years stay out").containsExactly(
                "Rechnung_2027-01.pdf", "Rechnung_2027-01.xml", "Rechnung_2027-02.pdf", "Rechnung_2027-03.xml",
                "Inhalt.csv");
        assertThat(entries).containsEntry("Rechnung_2027-01.pdf", "%PDF-1").containsEntry("Rechnung_2027-03.xml", "<x>3</x>")
                .containsEntry("Inhalt.csv", String.valueOf((char) 0xFEFF) + ArchiveService.HEAD + "\r\n"
                + "\"2027-01\";10.01.2027;\"Verlag am Fluss GmbH\";Rechnung_2027-01.pdf;6;sha-2027-01\r\n"
                + "\"2027-01\";10.01.2027;\"Verlag am Fluss GmbH\";Rechnung_2027-01.xml;8;sha-2027-01\r\n"
                + "\"2027-02\";01.03.2027;\"Verlag am Fluss GmbH\";Rechnung_2027-02.pdf;6;sha-2027-02\r\n"
                + "\"2027-03\";01.03.2027;\"Verlag am Fluss GmbH\";Rechnung_2027-03.xml;8;sha-2027-03\r\n");
        verify(documents, never()).list(4);
        verify(documents, never()).list(5);
    }

    @Test
    void twoNumbersWithTheSameFileNameStayApart() throws IOException {
        Invoice slash = invoice(6, InvoiceStatus.ISSUED, "2027/11", LocalDate.of(2027, 2, 1));
        Invoice hyphen = invoice(7, InvoiceStatus.ISSUED, "-2027-11", LocalDate.of(2027, 2, 2));
        when(invoices.list()).thenReturn(List.of(slash, hyphen));
        when(documents.restore("{}")).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(documents.list(6)).thenReturn(List.of(new StoredDocument(60L, 6L, DocumentKind.PDF, "Rechnung_2027-11.pdf",
                new byte[] {1}, "a", 1, Instant.EPOCH)));
        when(documents.list(7)).thenReturn(List.of(new StoredDocument(70L, 7L, DocumentKind.PDF, "Rechnung_2027-11.pdf",
                new byte[] {2}, "b", 1, Instant.EPOCH)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(2027, out);
        Map<String, String> entries = unzip(out.toByteArray());
        assertThat(entries.keySet()).containsExactly("Rechnung_2027-11.pdf", "Rechnung_2027-11_7.pdf", "Inhalt.csv");
        assertThat(entries.get("Inhalt.csv")).contains("\"2027/11\";01.02.2027;\"Verlag am Fluss GmbH\";Rechnung_2027-11.pdf;")
                .as("a number with a leading hyphen is no formula")
                .contains("\"'-2027-11\";02.02.2027;\"Verlag am Fluss GmbH\";Rechnung_2027-11_7.pdf;");
    }

    @Test
    void aRenamedFileNeverHitsAnotherName() {
        java.util.Set<String> names = new java.util.HashSet<>();
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11_7.pdf", 3)).isEqualTo("Rechnung_2027-11_7.pdf");
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11.pdf", 6)).isEqualTo("Rechnung_2027-11.pdf");
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11.pdf", 7)).isEqualTo("Rechnung_2027-11_7_2.pdf");
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11.pdf", 8)).isEqualTo("Rechnung_2027-11_8.pdf");
        assertThat(names).hasSize(4);
    }

    @Test
    void anEmptyYearHasTheContentsOnly() throws IOException {
        when(invoices.list()).thenReturn(List.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(2020, out);
        assertThat(unzip(out.toByteArray())).containsExactly(Map.entry("Inhalt.csv", String.valueOf((char) 0xFEFF) + ArchiveService.HEAD
                + "\r\n"));
    }

    @Test
    void aBrokenOutputFails() {
        when(invoices.list()).thenReturn(List.of());
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };
        assertThatThrownBy(() -> service.write(2020, broken)).isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("disk full");
    }

    @Test
    void neutralizesFormulasInTheCustomerName() {
        assertThat(ArchiveService.quoted("=HYPERLINK()")).isEqualTo("\"'=HYPERLINK()\"");
        assertThat(ArchiveService.quoted("Verlag \"X\"")).isEqualTo("\"Verlag \"\"X\"\"\"");
        assertThat(ArchiveService.quoted("")).isEqualTo("\"\"");
        for (String start : new String[] {"+", "-", "@", "\t", "\r"}) {
            assertThat(ArchiveService.quoted(start + "a")).startsWith("\"'");
        }
    }
}
