package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.archive.ArchiveService;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.ExternalInvoiceService;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceIntegrity;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.OriginalStore;
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.invoice.SampleOffice;
import io.github.grigoriev.im.numbering.ManualNumbers;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * External invoices against PostgreSQL: the number of the file against the counter, the rollback without a gap, the
 * sealed original files, the archive and the Stornorechnung. Each test uses its own year.
 */
@SpringBootTest
@Import(TestDatabase.class)
class ExternalInvoiceIT {

    private static final byte[] XLSX = SampleOffice.workbook();

    @Autowired
    private ExternalInvoiceService externals;

    @Autowired
    private IssueService issues;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private DocumentStore documents;

    @Autowired
    private OriginalStore originals;

    @Autowired
    private InvoiceIntegrity integrity;

    @Autowired
    private PaymentService payments;

    @Autowired
    private ArchiveService archive;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private NumberService numbers;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ManualNumbers manual;

    private Customer customer;

    @BeforeEach
    void profileAndCustomer() {
        SellerTestData.save(sellers, SellerTestData.profile());
        Customer verlag = CustomerTestData.verlag();
        customer = customers.create(new Customer(null, verlag.name(), verlag.street(), verlag.postcode(),
                verlag.city(), verlag.countryCode(), null, verlag.email(), null, null, InvoiceFormat.EXTERNAL, null));
        numbers.savePattern("{YYYY}-{NN}");
    }

    /** A complete external draft with a §19 line and a teaching line, as an old invoice may have them. */
    private Invoice draft(LocalDate date) {
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        form.setIssueDate(date);
        form.setServiceFrom(date);
        form.setServiceTo(date);
        LineForm reading = form.getLines().getFirst();
        reading.setText("Lesung");
        reading.setAmount("400");
        reading.setCategory(TaxCategory.KLEINUNTERNEHMER);
        LineForm teaching = new LineForm();
        teaching.setText("Kurs");
        teaching.setLumpSum(true);
        teaching.setAmount("300");
        teaching.setCategory(TaxCategory.EXEMPT_4_21);
        form.getLines().add(teaching);
        return invoices.saveDraft(draft.id(), form);
    }

    @Test
    void registersWithTheNextNumberAndKeepsTheSealedFile() throws IOException {
        Invoice draft = draft(LocalDate.of(2002, 3, 18));
        assertThat(draft.format()).as("the customer names the format").isEqualTo(InvoiceFormat.EXTERNAL);
        assertThat(invoices.problems(draft)).isEmpty();
        assertThat(externals.nextNumber(draft)).contains("2002-01");

        long draftId = draft.id();
        Long draftVersion = draft.version();
        assertThatThrownBy(() -> issues.issue(draftId, draftVersion)).isInstanceOf(InvoiceException.class)
                .hasMessage("Eine externe Rechnung wird mit ihrer Datei erfasst, nicht ausgestellt.");
        assertThat(externals.register(draft.id(), draft.version(), "2002-01", "März.xlsx", XLSX))
                .isEqualTo("2002-01");

        Invoice registered = invoices.find(draft.id()).orElseThrow();
        assertThat(registered.status()).isEqualTo(InvoiceStatus.ISSUED);
        InvoiceDocument snapshot = documents.verifiedSnapshot(registered);
        assertThat(snapshot.total()).isEqualByComparingTo("700.00");
        assertThat(snapshot.format()).isEqualTo(InvoiceFormat.EXTERNAL);
        assertThat(documents.list(draft.id())).as("no PDF or XML of the app").isEmpty();
        assertThat(originals.list(draft.id())).singleElement().satisfies(file -> {
            assertThat(file.filename()).isEqualTo("März.xlsx");
            assertThat(file.content()).isEqualTo(XLSX);
        });
        assertThat(integrity.check(registered).intact()).isTrue();

        PaymentForm paid = new PaymentForm();
        paid.setPaidOn(LocalDate.of(2002, 3, 30));
        paid.setAmount("700,00");
        assertThat(payments.add(draft.id(), paid)).isEqualTo(InvoiceStatus.PAID);

        byte[] pdf = "%PDF-1.7 Kopie".getBytes(StandardCharsets.US_ASCII);
        externals.addOriginal(draft.id(), "März.pdf", pdf);
        assertThat(originals.list(draft.id())).hasSize(2);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        archive.write(2002, zip);
        assertThat(entries(zip.toByteArray())).containsExactly("März.xlsx", "März.pdf", "Inhalt.csv");
    }

    @Test
    void refusesAnotherNumberWithoutAGap() {
        Invoice first = draft(LocalDate.of(2053, 2, 1));
        long firstId = first.id();
        Long firstVersion = first.version();
        assertThatThrownBy(() -> externals.register(firstId, firstVersion, "2053-07", "a.xlsx", XLSX))
                .isInstanceOf(InvoiceException.class).hasMessageStartingWith("Die nächste Rechnungsnummer ist 2053-01");
        assertThat(invoices.find(first.id()).orElseThrow().isDraft()).isTrue();
        assertThat(externals.nextNumber(first)).as("the counter went back").contains("2053-01");
        assertThat(originals.list(first.id())).isEmpty();
        assertThat(externals.register(first.id(), first.version(), "2053-01", "a.xlsx", XLSX)).isEqualTo("2053-01");
        long stillDraft = draft(LocalDate.of(2053, 2, 2)).id();
        assertThatThrownBy(() -> externals.addOriginal(stillDraft, "b.pdf", XLSX))
                .isInstanceOf(InvoiceException.class).hasMessage("Dateien gehören nur zu einer erfassten externen Rechnung.");
    }

    @Test
    void theDatabaseKeepsTheOriginalFiles() {
        Invoice draft = draft(LocalDate.of(2054, 5, 1));
        externals.register(draft.id(), draft.version(), "2054-01", "a.xlsx", XLSX);
        long id = draft.id();
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice_original SET filename = 'b.xlsx' WHERE invoice_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice_original WHERE invoice_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        String hash = "a".repeat(64);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO invoice_original (invoice_id, filename, media_type, content, "
                + "sha256, size, seal, created_at, created_by) VALUES (?, 'a.txt', 'text/plain', '\\x00', ?, 1, ?, now(), "
                + "'x')", id, hash, hash)).as("only the four types").isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT format FROM invoice WHERE id = ?", String.class, id)).isEqualTo("EXTERNAL");
    }

    @Test
    void aStornorechnungOfAnExternalInvoiceIsExternalToo() {
        Invoice draft = draft(LocalDate.of(2055, 6, 1));
        externals.register(draft.id(), draft.version(), "2055-01", "a.xlsx", XLSX);
        Invoice storno = invoices.createCancellation(draft.id());
        assertThat(storno.format()).isEqualTo(InvoiceFormat.EXTERNAL);
        LocalDate today = storno.issueDate();
        Invoice stornoDraft = invoices.find(storno.id()).orElseThrow();
        String number = externals.nextNumber(stornoDraft).orElseThrow();
        assertThat(number).startsWith(today.getYear() + "-");
        externals.register(stornoDraft.id(), stornoDraft.version(), number, "Storno.pdf",
                "%PDF-1.7 Storno".getBytes(StandardCharsets.US_ASCII));
        assertThat(invoices.find(draft.id()).orElseThrow().status()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(documents.verifiedSnapshot(invoices.find(storno.id()).orElseThrow()).cancels().number())
                .isEqualTo("2055-01");
    }

    @Test
    void aYearBeforeTheAppTakesTheNumbersOfItsFilesAndCloses() {
        manual.setManual(2052, true);
        Invoice third = draft(LocalDate.of(2052, 3, 1));
        Invoice first = draft(LocalDate.of(2052, 1, 10));
        assertThat(externals.nextNumber(third)).as("the file names the number").isEmpty();
        assertThat(externals.register(third.id(), third.version(), "2052-03", "c.xlsx", XLSX)).isEqualTo("2052-03");
        assertThat(externals.register(first.id(), first.version(), "2052-01", "a.xlsx", XLSX)).isEqualTo("2052-01");
        Invoice again = draft(LocalDate.of(2052, 4, 1));
        long againId = again.id();
        Long againVersion = again.version();
        assertThatThrownBy(() -> externals.register(againId, againVersion, "2052-3", "d.xlsx", XLSX))
                .isInstanceOf(InvoiceException.class).hasMessage("Die Nummer 2052-3 gibt es schon: Rechnung 2052-03.");

        Invoice ofTheApp = invoices.createDraft(customers.create(CustomerTestData.verlag()).id(), null);
        DraftForm form = DraftForm.of(ofTheApp);
        form.setIssueDate(LocalDate.of(2052, 5, 1));
        form.setServiceFrom(LocalDate.of(2052, 5, 1));
        form.setServiceTo(LocalDate.of(2052, 5, 1));
        form.getLines().getFirst().setText("Lesung");
        form.getLines().getFirst().setAmount("100");
        Invoice appDraft = invoices.saveDraft(ofTheApp.id(), form);
        long appId = appDraft.id();
        Long appVersion = appDraft.version();
        assertThatThrownBy(() -> issues.issue(appId, appVersion)).isInstanceOf(InvoiceException.class)
                .hasMessageStartingWith("Für 2052 werden die Nummern von Hand erfasst.");

        assertThatThrownBy(() -> manual.close(2052)).isInstanceOf(SettingsException.class)
                .hasMessage("Diese Nummern fehlen noch, ohne Grund: 2052-02.");
        manual.explainGap(2052, 2, "Nummer verschrieben");
        assertThatThrownBy(() -> jdbc.update("UPDATE number_gap SET reason = 'x' WHERE year = 2052"))
                .as("a reason stays").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM number_gap WHERE year = 2052"))
                .isInstanceOf(DataAccessException.class);
        assertThat(manual.close(2052)).isEqualTo(4);
        assertThat(externals.nextNumber(again)).as("the counter continues").contains("2052-04");
        assertThat(externals.register(againId, againVersion, "2052-04", "d.xlsx", XLSX)).isEqualTo("2052-04");
        assertThat(manual.book(2052).closed()).isTrue();
        assertThatThrownBy(() -> manual.setManual(2052, true)).isInstanceOf(SettingsException.class);
    }

    @Test
    void twoRegistrationsOfOneNumberInTwoFormsAtOnceTakeItOnce() throws InterruptedException {
        manual.setManual(2074, true);
        Invoice one = draft(LocalDate.of(2074, 3, 1));
        Invoice other = draft(LocalDate.of(2074, 3, 2));
        List<Object> outcomes = Parallel.run(List.of(
                () -> externals.register(one.id(), one.version(), "2074-3", "a.xlsx", XLSX),
                () -> externals.register(other.id(), other.version(), "2074-03", "b.xlsx", XLSX)));
        assertThat(outcomes).filteredOn(String.class::isInstance).as("one registration wins").hasSize(1);
        assertThat(outcomes).filteredOn(InvoiceException.class::isInstance).singleElement()
                .satisfies(refused -> assertThat(((InvoiceException) refused).getMessage())
                        .matches("Die Nummer 2074-0?3 gibt es schon: Rechnung 2074-0?3\\."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice WHERE status <> 'DRAFT' AND issue_date "
                + "BETWEEN '2074-01-01' AND '2074-12-31'", Integer.class)).isEqualTo(1);
    }

    private static List<String> entries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
