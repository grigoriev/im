package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.EInvoiceFacts.Difference;
import io.github.grigoriev.im.einvoice.EInvoiceWriter;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.einvoice.MustangInvoiceMapper;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.Seal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InvoiceIntegrityTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");

    private final StoredDocumentRepository repository = mock(StoredDocumentRepository.class);
    private final Seal seal = new Seal("test-only-integrity-key-0123456789abcdef");
    private final DocumentStore store = new DocumentStore(repository, JsonMapper.builder().findAndAddModules().build(),
            seal);
    private final EInvoiceWriter writer = new EInvoiceWriter(new MustangInvoiceMapper());
    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(SampleInvoices.templates());
    private final InvoiceService invoices = mock(InvoiceService.class);
    private final OriginalStore originals = mock(OriginalStore.class);
    private final InvoiceIntegrity integrity = new InvoiceIntegrity(invoices, store, originals, writer);

    /** The seal of the snapshot in the database, {@code null} for no row. */
    private String storedSeal;
    private boolean row = true;

    @BeforeEach
    void database() {
        when(repository.findSnapshotSeal(anyLong())).thenAnswer(call -> row ? storedSeal : null);
    }

    private Invoice issued(long id, InvoiceDocument document) {
        String snapshot = store.snapshot(document);
        storedSeal = seal.snapshot(id, document.number(), snapshot);
        return invoice(id, document.number(), document.format(), snapshot);
    }

    private static Invoice invoice(long id, String number, InvoiceFormat format, String snapshot) {
        LocalDate day = LocalDate.of(2027, 3, 18);
        boolean draft = snapshot == null;
        return new Invoice(id, draft ? InvoiceStatus.DRAFT : InvoiceStatus.ISSUED, 2L, number, format, day, day, day,
                14, null, null, null, null, List.of(), NOW, NOW, 1L, draft ? null : NOW, snapshot, null);
    }

    private StoredDocument sealed(long invoiceId, DocumentKind kind, byte[] content) {
        String sha256 = Seal.sha256(content);
        return new StoredDocument(null, invoiceId, kind, kind.filename("2027-10"), content, sha256, content.length,
                NOW, seal.invoiceDocument(invoiceId, kind.name(), sha256));
    }

    private void files(long invoiceId, StoredDocument... files) {
        when(repository.findByInvoiceIdOrderByKindAsc(invoiceId)).thenReturn(List.of(files));
    }

    /** The PDF and XML of a ZUGFeRD invoice, as the issue stores them. */
    private List<StoredDocument> zugferd(long invoiceId, InvoiceDocument document) {
        byte[] pdf = writer.zugferd(document, renderer.render(document));
        return List.of(sealed(invoiceId, DocumentKind.PDF, pdf), sealed(invoiceId, DocumentKind.XML,
                writer.embeddedXml(pdf)));
    }

    @Test
    void findsNothingWrongWithAnInvoiceAsItWasIssued() {
        InvoiceDocument sale = SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931);
        Invoice invoice = issued(6, sale);
        files(6, zugferd(6, sale).toArray(StoredDocument[]::new));
        InvoiceIntegrity.Report report = integrity.check(invoice);
        assertThat(report.problems()).isEmpty();
        assertThat(report.differences()).isEmpty();
        assertThat(report.intact()).isTrue();
        when(invoices.list()).thenReturn(List.of(invoice, invoice(7, null, InvoiceFormat.XRECHNUNG, null)));
        assertThat(integrity.damaged()).as("a draft has nothing to check").isEmpty();
    }

    @Test
    void checksTheOriginalFilesOfAnExternalInvoice() {
        InvoiceDocument reading = SampleInvoices.reading(InvoiceFormat.EXTERNAL);
        Invoice invoice = issued(8, reading);
        InvoiceOriginal file = new InvoiceOriginal(1, 8, "Rechnung.xlsx", OriginalStore.XLSX, new byte[] {1}, "abc", 1,
                "seal", NOW, "elena");
        when(originals.list(8)).thenReturn(List.of(file));
        when(originals.problems(file)).thenReturn(List.of());
        assertThat(integrity.check(invoice).intact()).isTrue();

        when(originals.problems(file)).thenReturn(List.of("Rechnung.xlsx: Das Siegel passt nicht."));
        assertThat(integrity.check(invoice).problems()).containsExactly("Rechnung.xlsx: Das Siegel passt nicht.");

        when(originals.list(8)).thenReturn(List.of());
        assertThat(integrity.check(invoice).problems()).containsExactly(InvoiceIntegrity.NO_ORIGINAL);
        assertThat(integrity.check(invoice).differences()).isEmpty();

        String broken = "{broken";
        storedSeal = seal.snapshot(8, "2027-10", broken);
        Invoice unreadable = invoice(8, "2027-10", InvoiceFormat.EXTERNAL, broken);
        when(originals.list(8)).thenReturn(List.of(file));
        when(originals.problems(file)).thenReturn(List.of());
        assertThat(integrity.check(unreadable).problems()).containsExactly(InvoiceIntegrity.SNAPSHOT_UNREADABLE);
    }

    @Test
    void namesWhatAChangeOfTheXmlAltered() {
        InvoiceDocument sale = SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931);
        Invoice invoice = issued(6, sale);
        List<StoredDocument> issued = zugferd(6, sale);
        String xml = new String(issued.get(1).content(), StandardCharsets.UTF_8).replace(">146.33<", ">1463.30<");
        byte[] changed = xml.getBytes(StandardCharsets.UTF_8);
        StoredDocument forged = new StoredDocument(null, 6L, DocumentKind.XML, "Rechnung_2027-10.xml", changed,
                Seal.sha256(changed), changed.length, NOW, issued.get(1).seal());
        files(6, issued.get(0), forged);
        InvoiceIntegrity.Report report = integrity.check(invoice);
        assertThat(report.problems()).containsExactly(
                "Rechnung_2027-10.xml: Das Siegel passt nicht. Prüfsumme oder Zuordnung der Datei wurden außerhalb "
                        + "der App geändert.",
                InvoiceIntegrity.XML_DIFFERS, InvoiceIntegrity.PDF_XML_DIFFERS);
        assertThat(report.differences()).containsExactly(new Difference("Gesamtbetrag", "146,33 €", "1.463,30 €"));
        when(invoices.list()).thenReturn(List.of(invoice));
        assertThat(integrity.damaged()).extracting(Damage::invoiceId, Damage::what, Damage::href)
                .containsExactly(tuple(6L, "Rechnung 2027-10", "/invoices/6"));
    }

    @Test
    void findsAChangedOrMissingSnapshotSeal() {
        InvoiceDocument sale = SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931);
        Invoice invoice = issued(6, sale);
        files(6, zugferd(6, sale).toArray(StoredDocument[]::new));
        storedSeal = seal.snapshot(6, "2027-11", invoice.snapshot());
        assertThat(integrity.check(invoice).problems()).containsExactly(InvoiceIntegrity.SNAPSHOT_CHANGED);
        row = false;
        assertThat(integrity.check(invoice).problems()).containsExactly(InvoiceIntegrity.SNAPSHOT_CHANGED);
    }

    @Test
    void findsMissingFilesAndAnUnreadableSnapshot() {
        InvoiceDocument sale = SampleInvoices.sale(InvoiceFormat.XRECHNUNG);
        Invoice invoice = issued(6, sale);
        files(6);
        assertThat(integrity.check(invoice).problems()).containsExactly("Die Datei Rechnung_2027-10.pdf fehlt.",
                "Die Datei Rechnung_2027-10.xml fehlt.");
        Invoice broken = invoice(8, "2027-10", InvoiceFormat.XRECHNUNG, "kaputt");
        storedSeal = seal.snapshot(8, "2027-10", "kaputt");
        files(8, sealed(8, DocumentKind.XML, writer.xrechnung(sale)));
        assertThat(integrity.check(broken).problems()).containsExactly("Die Datei Rechnung_2027-10.pdf fehlt.",
                InvoiceIntegrity.SNAPSHOT_UNREADABLE);
    }

    @Test
    void findsAnUnreadableXmlAndAPdfWithoutXml() {
        InvoiceDocument sale = SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931);
        Invoice invoice = issued(6, sale);
        files(6, sealed(6, DocumentKind.PDF, "%PDF-kaputt".getBytes(StandardCharsets.US_ASCII)),
                sealed(6, DocumentKind.XML, "kein XML".getBytes(StandardCharsets.US_ASCII)));
        InvoiceIntegrity.Report report = integrity.check(invoice);
        assertThat(report.problems()).containsExactly(InvoiceIntegrity.XML_UNREADABLE, InvoiceIntegrity.PDF_WITHOUT_XML);
        assertThat(report.differences()).isEmpty();
    }

    @Test
    void comparesTheXmlOfAnXRechnungWithoutItsPdf() {
        InvoiceDocument sale = SampleInvoices.sale(InvoiceFormat.XRECHNUNG);
        Invoice invoice = issued(6, sale);
        files(6, sealed(6, DocumentKind.PDF, renderer.render(sale)), sealed(6, DocumentKind.XML, writer.xrechnung(sale)));
        assertThat(integrity.check(invoice).problems()).isEmpty();
        files(6, sealed(6, DocumentKind.XML, writer.xrechnung(sale)));
        assertThat(integrity.check(invoice).problems()).as("only the PDF is missing")
                .containsExactly("Die Datei Rechnung_2027-10.pdf fehlt.");
    }

    @Test
    void keepsCopiesOfItsLists() {
        List<String> problems = new ArrayList<>(List.of("x"));
        InvoiceIntegrity.Report report = new InvoiceIntegrity.Report(problems, new ArrayList<>());
        problems.clear();
        assertThat(report.problems()).containsExactly("x");
        assertThat(report.intact()).isFalse();
    }
}
