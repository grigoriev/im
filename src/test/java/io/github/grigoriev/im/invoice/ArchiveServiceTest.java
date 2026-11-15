package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentFile;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.ItSamples;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveServiceTest {

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final DocumentStore documents = mock(DocumentStore.class);
    private final DocumentService businessDocuments = mock(DocumentService.class);
    private final InvoiceIntegrity integrity = mock(InvoiceIntegrity.class);
    @TempDir
    private Path folder;
    private ArchiveService service;

    @BeforeEach
    void intact() {
        service = new ArchiveService(invoices, documents, businessDocuments, integrity, folder);
        when(documents.verified(any())).thenAnswer(call -> call.getArgument(0));
        when(integrity.check(any())).thenReturn(new InvoiceIntegrity.Report(List.of(), List.of()));
    }

    private static Invoice invoice(long id, InvoiceStatus status, String number, LocalDate date) {
        boolean draft = status == InvoiceStatus.DRAFT;
        return new Invoice(id, status, 2L, number, InvoiceFormat.ZUGFERD_EN16931, date, date, date, 14, null, null, null,
                null, List.of(), Instant.EPOCH, Instant.EPOCH, 1L, draft ? null : Instant.EPOCH, draft ? null : "{}", null);
    }

    private static StoredDocument file(long invoiceId, DocumentKind kind, String number, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new StoredDocument(invoiceId * 10, invoiceId, kind, kind.filename(number), bytes, "sha-" + number,
                bytes.length, Instant.EPOCH, "seal");
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
        when(documents.verifiedSnapshot(any())).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(documents.list(1)).thenReturn(List.of(file(1, DocumentKind.PDF, "2027-01", "%PDF-1"),
                file(1, DocumentKind.XML, "2027-01", "<x>1</x>")));
        when(documents.list(2)).thenReturn(List.of(file(2, DocumentKind.PDF, "2027-02", "%PDF-2")));
        when(documents.list(3)).thenReturn(List.of(file(3, DocumentKind.XML, "2027-03", "<x>3</x>")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(2027, out);
        assertThat(folder).as("the archive left its folder").isEmptyDirectory();
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
        when(documents.verifiedSnapshot(any())).thenReturn(ItSamples.withBuyerContact(
                SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931), "Frau Berg"));
        when(documents.list(6)).thenReturn(List.of(new StoredDocument(60L, 6L, DocumentKind.PDF, "Rechnung_2027-11.pdf",
                new byte[] {1}, "a", 1, Instant.EPOCH, "seal")));
        when(documents.list(7)).thenReturn(List.of(new StoredDocument(70L, 7L, DocumentKind.PDF, "Rechnung_2027-11.pdf",
                new byte[] {2}, "b", 1, Instant.EPOCH, "seal")));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(2027, out);
        Map<String, String> entries = unzip(out.toByteArray());
        assertThat(entries.keySet()).containsExactly("Rechnung_2027-11.pdf", "Rechnung_2027-11_7.pdf", "Inhalt.csv");
        assertThat(entries.get("Inhalt.csv")).contains("\"2027/11\";01.02.2027;\"Verlag am Fluss GmbH · Frau Berg\";Rechnung_2027-11.pdf;")
                .as("a number with a leading hyphen is no formula")
                .contains("\"'-2027-11\";02.02.2027;\"Verlag am Fluss GmbH · Frau Berg\";Rechnung_2027-11_7.pdf;");
    }

    @Test
    void namesTheBrokenFilesOfAYearBeforeTheArchiveGoesOut() {
        Invoice good = invoice(1, InvoiceStatus.PAID, "2027-01", LocalDate.of(2027, 1, 10));
        Invoice changed = invoice(2, InvoiceStatus.SENT, "2027-02", LocalDate.of(2027, 2, 10));
        when(invoices.list()).thenReturn(List.of(good, changed, invoice(3, InvoiceStatus.SENT, "2026-09",
                LocalDate.of(2026, 12, 31))));
        when(integrity.check(good)).thenReturn(new InvoiceIntegrity.Report(List.of(), List.of()));
        when(integrity.check(changed)).thenReturn(new InvoiceIntegrity.Report(List.of("x"), List.of()));
        BusinessDocument intact = new BusinessDocument(7, DocumentCategory.OTHER, "A", LocalDate.of(2027, 1, 1), null,
                "", null, null, "a.pdf", "application/pdf", "h", 1, Instant.EPOCH, "anna");
        BusinessDocument broken = new BusinessDocument(8, DocumentCategory.OTHER, "B", LocalDate.of(2027, 1, 1), null,
                "", null, null, "b.pdf", "application/pdf", "h", 1, Instant.EPOCH, "anna");
        when(businessDocuments.ofYear(2027)).thenReturn(List.of(intact, broken));
        when(businessDocuments.problems(8)).thenReturn(List.of("y"));
        assertThat(service.damaged(2027)).containsExactly("Rechnung 2027-02", "Dokument b.pdf");
        verify(integrity, never()).check(argThat(invoice -> invoice.id() == 3));
    }

    @Test
    void aMissingOrChangedFileStopsTheArchiveBeforeItsFirstEntry() {
        Invoice invoice = invoice(1, InvoiceStatus.PAID, "2027-01", LocalDate.of(2027, 1, 10));
        when(invoices.list()).thenReturn(List.of(invoice));
        when(integrity.check(invoice)).thenReturn(new InvoiceIntegrity.Report(List.of("Die Datei fehlt."), List.of()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> service.write(2027, out)).isInstanceOf(InvoiceException.class).hasMessage(
                "Das Archiv bleibt aus, denn diese Dateien fehlen, sind beschädigt oder wurden außerhalb der App "
                        + "geändert: Rechnung 2027-01.");
        assertThat(out.size()).as("not a single byte").isZero();
    }

    @Test
    void aFileChangedAfterTheCheckStopsTheArchiveBeforeItsFirstByte() {
        when(invoices.list()).thenReturn(List.of(invoice(1, InvoiceStatus.PAID, "2027-01", LocalDate.of(2027, 1, 10)),
                invoice(2, InvoiceStatus.PAID, "2027-02", LocalDate.of(2027, 1, 11))));
        when(documents.verifiedSnapshot(any())).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(documents.list(1)).thenReturn(List.of(file(1, DocumentKind.PDF, "2027-01", "%PDF-1")));
        StoredDocument changed = file(2, DocumentKind.PDF, "2027-02", "%PDF-2");
        when(documents.list(2)).thenReturn(List.of(changed));
        when(documents.verified(changed)).thenThrow(new InvoiceException(List.of("kaputt")));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> service.write(2027, out)).hasMessage("kaputt");
        assertThat(out.size()).as("the first invoice was fine, yet nothing left").isZero();
        assertThat(folder).as("the half archive is gone").isEmptyDirectory();
    }

    @Test
    void aRenamedFileNeverHitsAnotherName() {
        Set<String> names = new HashSet<>();
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11_7.pdf", 3)).isEqualTo("Rechnung_2027-11_7.pdf");
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11.pdf", 6)).isEqualTo("Rechnung_2027-11.pdf");
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11.pdf", 7)).isEqualTo("Rechnung_2027-11_7_2.pdf");
        assertThat(ArchiveService.unique(names, "Rechnung_2027-11.pdf", 8)).isEqualTo("Rechnung_2027-11_8.pdf");
        assertThat(names).hasSize(4);
    }

    @Test
    void growsTheArchiveInTheTemporaryFolderOfJava() throws IOException {
        when(invoices.list()).thenReturn(List.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ArchiveService(invoices, documents, businessDocuments, integrity).write(2020, out);
        assertThat(unzip(out.toByteArray())).containsOnlyKeys("Inhalt.csv");
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

    @Test
    void namesAFileWithoutExtensionApartAtItsEnd() {
        Set<String> names = new HashSet<>();
        assertThat(ArchiveService.unique(names, "Bescheid", 4)).isEqualTo("Bescheid");
        assertThat(ArchiveService.unique(names, "Bescheid", 5)).isEqualTo("Bescheid_5");
        assertThat(ArchiveService.unique(names, ".hidden", 6)).isEqualTo(".hidden");
        assertThat(ArchiveService.unique(names, ".hidden", 7)).isEqualTo(".hidden_7");
    }

    private static BusinessDocument document(long id, String title, String filename, Integer year, Long replacedBy) {
        return new BusinessDocument(id, DocumentCategory.TAX_ASSESSMENT, title, LocalDate.of(2027, 5, 10), year, "",
                null, replacedBy, filename, "application/pdf", "sha-" + id, 3, Instant.EPOCH, "anna");
    }

    @Test
    void putsTheBusinessDocumentsOfTheYearIntoTheirFolder() throws IOException {
        when(invoices.list()).thenReturn(List.of());
        when(businessDocuments.ofYear(2026)).thenReturn(List.of(document(1, "Bescheid 2026", "Bescheid.pdf", 2026, 2L),
                document(2, "=Bescheid 2026", "Bescheid.pdf", null, null)));
        when(businessDocuments.file(1)).thenReturn(Optional.of(new DocumentFile("Bescheid.pdf", "application/pdf",
                "alt".getBytes(StandardCharsets.UTF_8))));
        when(businessDocuments.file(2)).thenReturn(Optional.of(new DocumentFile("Bescheid.pdf", "application/pdf",
                "neu".getBytes(StandardCharsets.UTF_8))));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(2026, out);
        Map<String, String> entries = unzip(out.toByteArray());
        assertThat(entries.keySet()).containsExactly("Inhalt.csv", "Dokumente/Bescheid.pdf", "Dokumente/Bescheid_2.pdf",
                "Dokumente/Inhalt.csv");
        assertThat(entries).containsEntry("Dokumente/Bescheid.pdf", "alt").containsEntry("Dokumente/Bescheid_2.pdf", "neu")
                .containsEntry("Dokumente/Inhalt.csv", String.valueOf((char) 0xFEFF)
                + ArchiveService.DOCUMENT_HEAD + "\r\n"
                + "\"Steuerbescheid\";\"Bescheid 2026\";10.05.2027;2026;ersetzt;\"Dokumente/Bescheid.pdf\";3;sha-1\r\n"
                + "\"Steuerbescheid\";\"'=Bescheid 2026\";10.05.2027;;aktuell;\"Dokumente/Bescheid_2.pdf\";3;sha-2\r\n");
    }

    @Test
    void offersTheYearsOfTheDocuments() {
        when(businessDocuments.years()).thenReturn(Set.of(2024));
        assertThat(service.years()).containsExactly(2024);
    }
}
