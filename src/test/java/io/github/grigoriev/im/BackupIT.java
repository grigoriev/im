package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.backup.BackupException;
import io.github.grigoriev.im.backup.BackupService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.expense.Expense;
import io.github.grigoriev.im.expense.ExpenseCategory;
import io.github.grigoriev.im.expense.ExpenseForm;
import io.github.grigoriev.im.expense.ExpenseService;
import io.github.grigoriev.im.integrity.Seal;
import io.github.grigoriev.im.integrity.SealMigration;
import io.github.grigoriev.im.invoice.DispatchChannel;
import io.github.grigoriev.im.invoice.DispatchForm;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.invoice.TemplateService;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The full export against PostgreSQL: it reads the shared test database into a second, freshly migrated database on
 * the same server, and the export of that copy equals the original entry by entry. The copy keeps its triggers and
 * continues its identities.
 */
@SpringBootTest
@Import(TestDatabase.class)
class BackupIT {

    @Autowired
    private BackupService backups;

    @Autowired
    private JsonMapper json;

    @Autowired
    private TestDatabase.Owner owner;

    @Autowired
    private SealMigration sealMigration;

    @Autowired
    private Seal seal;

    @Autowired
    private ExpenseService expenses;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SellerService sellers;

    @Autowired
    private CustomerService customers;

    @Autowired
    private TemplateService templates;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private IssueService issues;

    @Autowired
    private PaymentService payments;

    @Autowired
    private DispatchService dispatches;

    @Autowired
    private NumberService numbers;

    @Autowired
    private ThresholdService threshold;

    @Autowired
    private DocumentService documents;

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    /** An issued, sent, paid and cancelled invoice of 2013 with its Stornorechnung, a template and a baseline. */
    private void someOfEverything() {
        SellerTestData.save(sellers, SellerTestData.profile());
        numbers.savePattern("{YYYY}-{NN}");
        Customer customer = customers.create(CustomerTestData.verlag());
        templates.create(customer.id());
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        LocalDate day = LocalDate.of(2013, 4, 2);
        form.setIssueDate(day);
        form.setServiceFrom(day);
        form.setServiceTo(day);
        form.setBuyerReference(customer.buyerReference());
        LineForm line = form.getLines().getFirst();
        line.setText("Lesung „am Fluss“");
        line.setAmount("400");
        line.setCategory(TaxCategory.KLEINUNTERNEHMER);
        Invoice saved = invoices.saveDraft(draft.id(), form);
        issues.issue(saved.id(), saved.version());
        DispatchForm sent = new DispatchForm();
        sent.setSentOn(day);
        sent.setChannel(DispatchChannel.POST);
        dispatches.recordManual(saved.id(), sent);
        PaymentForm paid = new PaymentForm();
        paid.setPaidOn(day.plusDays(5));
        paid.setAmount("400,00");
        payments.add(saved.id(), paid);
        Invoice storno = invoices.createCancellation(saved.id());
        Invoice stornoDraft = invoices.find(storno.id()).orElseThrow();
        issues.issue(stornoDraft.id(), stornoDraft.version());
        threshold.saveBaseline(2012, "1.000,00");
        DocumentForm letter = new DocumentForm();
        letter.setCategory(DocumentCategory.TAX_OFFICE);
        letter.setTitle("Steuernummer");
        letter.setDocumentDate(day);
        BusinessDocument first = documents.add(letter, "Brief.pdf", "%PDF-1.4 erste".getBytes(StandardCharsets.US_ASCII));
        documents.replace(first.id(), letter, "Brief.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ExpenseForm book = new ExpenseForm();
        book.setPaidOn(day);
        book.setAmount("49,90");
        book.setCategory(ExpenseCategory.LITERATURE);
        book.setVendor("Buchhandlung");
        book.setDescription("Wörterbuch");
        expenses.add(book, "Quittung.pdf", "%PDF-1.4 Quittung".getBytes(StandardCharsets.US_ASCII));
        Expense twice = expenses.add(book, "Quittung.pdf", "%PDF-1.4 Quittung".getBytes(StandardCharsets.US_ASCII));
        expenses.cancel(twice.id(), "doppelt");
    }

    /**
     * A second database on the same server, freshly migrated, with the first admin of a new installation.
     *
     * @param backups the import into it, which checks the files with the seal given
     * @param transaction a transaction on it
     * @param jdbc it, as the app
     */
    private record Copy(BackupService backups, TransactionTemplate transaction, JdbcTemplate jdbc) {
    }

    private Copy copy(String database, Seal sealOfCopy) {
        owner.jdbc().sql("DROP DATABASE IF EXISTS " + database).update();
        owner.jdbc().sql("CREATE DATABASE " + database).update();
        Flyway.configure().dataSource(owner.dataSource(database)).locations("classpath:db/migration")
                .placeholders(Map.of("appuser", "im_app")).javaMigrations(sealMigration).load().migrate();
        DataSource copy = owner.appDataSource(database);
        DocumentService copyDocuments = new DocumentService(JdbcClient.create(copy), mock(AuditService.class),
                sealOfCopy, Clock.systemDefaultZone());
        ExpenseService copyExpenses = new ExpenseService(JdbcClient.create(copy), mock(AuditService.class),
                sealOfCopy, Clock.systemDefaultZone());
        BackupService into = new BackupService(JdbcClient.create(copy), json, mock(AuditService.class),
                List.of(copyDocuments, copyExpenses), Clock.systemDefaultZone());
        JdbcTemplate copyJdbc = new JdbcTemplate(copy);
        copyJdbc.update("INSERT INTO app_user (username, display_name, password_hash, role, enabled) "
                + "VALUES ('erster', 'Erster Admin', 'x', 'ADMIN', TRUE)");
        return new Copy(into, new TransactionTemplate(new DataSourceTransactionManager(copy)), copyJdbc);
    }

    /** The export with other bytes in the first file under the prefix. */
    private static byte[] changed(byte[] zip, String prefix) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean done = false;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip));
                ZipOutputStream changed = new ZipOutputStream(out)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                byte[] content = in.readAllBytes();
                changed.putNextEntry(new ZipEntry(entry.getName()));
                if (!done && entry.getName().startsWith(prefix)) {
                    content = "%PDF-1.4 falsch".getBytes(StandardCharsets.US_ASCII);
                    done = true;
                }
                changed.write(content);
            }
        }
        assertThat(done).as("the export has a file under " + prefix).isTrue();
        return out.toByteArray();
    }

    /** Expects the import to fail on its check and to leave the copy as it was, with its identities unmoved. */
    private static void refused(Copy copy, Path upload, String damaged) {
        assertThatThrownBy(() -> copy.transaction().executeWithoutResult(status -> copy.backups().restore(upload)))
                .isInstanceOf(BackupException.class).hasMessageContaining("nicht zu Prüfsumme oder Siegel passen")
                .hasMessageContaining(damaged);
        for (String table : List.of("customer", "invoice", "business_document", "expense")) {
            assertThat(copy.jdbc().queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
            assertThat(copy.jdbc().queryForObject("SELECT pg_sequence_last_value(pg_get_serial_sequence(?, 'id')"
                    + "::regclass)", Long.class, table)).as("the identity of " + table + " never moved").isNull();
        }
        assertThat(copy.jdbc().queryForList("SELECT username FROM app_user", String.class))
                .as("the first admin stays").containsExactly("erster");
    }

    @Test
    void aChangedFileOrAnotherKeyStopsTheImportAndLeavesTheInstallationEmpty(@TempDir Path folder)
            throws IOException {
        ExpenseForm book = new ExpenseForm();
        book.setPaidOn(LocalDate.of(2013, 5, 2));
        book.setAmount("12,00");
        book.setCategory(ExpenseCategory.LITERATURE);
        book.setVendor("Buchhandlung");
        book.setDescription("Duden");
        expenses.add(book, "Duden.pdf", "%PDF-1.4 Duden".getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        backups.export(original);
        Path intact = Files.write(folder.resolve("export.zip"), original.toByteArray());
        Path changed = Files.write(folder.resolve("changed.zip"), changed(original.toByteArray(), "files/expense/"));

        Copy copy = copy("im_refused", seal);
        refused(copy, changed, "Beleg ");
        Copy otherKey = copy("im_other_key", new Seal("another-key-than-the-one-of-the-export-0123"));
        refused(otherKey, intact, "Beleg Duden.pdf");

        copy.transaction().executeWithoutResult(status -> copy.backups().restore(intact));
        assertThat(copy.jdbc().queryForObject("SELECT count(*) FROM expense WHERE description = 'Duden'",
                Integer.class)).as("the intact export goes into the installation the refused one left empty")
                .isEqualTo(1);
    }

    @Test
    void anExportReadsBackIntoAnEmptyDatabaseExactly(@TempDir Path folder) throws IOException {
        someOfEverything();
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        backups.export(original);

        Copy copy = copy("im_copy", seal);
        BackupService into = copy.backups();
        TransactionTemplate transaction = copy.transaction();
        JdbcTemplate copyJdbc = copy.jdbc();

        Path upload = Files.write(folder.resolve("export.zip"), original.toByteArray());
        transaction.executeWithoutResult(status -> into.restore(upload));

        ByteArrayOutputStream again = new ByteArrayOutputStream();
        transaction.executeWithoutResult(status -> into.export(again));
        Map<String, byte[]> before = unzip(original.toByteArray());
        Map<String, byte[]> after = unzip(again.toByteArray());
        assertThat(after.keySet()).isEqualTo(before.keySet());
        for (String name : before.keySet()) {
            if (!name.equals("manifest.json")) {
                assertThat(after).as(name).containsEntry(name, before.get(name));
            }
        }
        assertThat(copyJdbc.queryForObject("SELECT count(*) FROM app_user WHERE username = 'erster'", Integer.class))
                .as("the users of the export replace the first admin").isZero();

        Long next = copyJdbc.queryForObject("INSERT INTO customer (name, street, postcode, city, country_code, format) "
                + "VALUES ('Neu', 'Weg 1', '12345', 'Ort', 'DE', 'ZUGFERD_EN16931') RETURNING id", Long.class);
        assertThat(next).as("the identity continues after the imported rows")
                .isGreaterThan(copyJdbc.queryForObject("SELECT max(id) FROM customer WHERE name <> 'Neu'", Long.class));
        assertThatThrownBy(() -> copyJdbc.update("UPDATE invoice SET intro = 'neu' WHERE number = '2013-01'"))
                .hasMessageContaining("immutable");
        assertThat(copyJdbc.queryForObject("SELECT status FROM invoice WHERE number = '2013-01'", String.class))
                .isEqualTo("CANCELLED");
        assertThatThrownBy(() -> copyJdbc.update("DELETE FROM business_document")).hasMessageContaining("is kept");
        assertThatThrownBy(() -> copyJdbc.update("DELETE FROM expense")).hasMessageContaining("is kept");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> into.restore(upload)))
                .isInstanceOf(BackupException.class).hasMessageContaining("schon Daten");
        assertThat(Arrays.equals(before.get("data/invoice.json"), after.get("data/invoice.json"))).isTrue();
    }
}
