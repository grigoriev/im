package io.github.grigoriev.im.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.TestDatabase;
import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditEvent;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentException;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.EInvoiceFacts.Difference;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceIntegrity;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The seals against PostgreSQL: the app connects without the right to switch a trigger off, and a change made in the
 * database by the owner shows on every way out. Each change is undone at the end, since the tests share the database.
 */
@SpringBootTest
@Import(TestDatabase.class)
class IntegrityIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestDatabase.Owner owner;

    @Autowired
    private SellerService sellers;

    @Autowired
    private CustomerService customers;

    @Autowired
    private NumberService numbers;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private IssueService issues;

    @Autowired
    private DocumentStore documents;

    @Autowired
    private InvoiceIntegrity integrity;

    @Autowired
    private DocumentService businessDocuments;

    @Autowired
    private IntegrityMonitor monitor;

    @Autowired
    private AuditService audit;

    @Autowired
    private SealMigration sealMigration;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void theAppCannotSwitchATriggerOffAndRefusesToRunAsTheOwner() {
        assertThatThrownBy(() -> jdbc.execute("ALTER TABLE invoice_document DISABLE TRIGGER invoice_document_frozen"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> jdbc.execute("SET session_replication_role = replica"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_event")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM flyway_schema_history"))
                .isInstanceOf(DataAccessException.class);
        ConnectionGuard asOwner = new ConnectionGuard(owner.jdbc());
        assertThatThrownBy(() -> asOwner.run(null)).isInstanceOf(IllegalStateException.class);
        new ConnectionGuard(JdbcClient.create(jdbc.getDataSource())).run(null);
    }

    @Test
    void theAppMayOnlyReadAndWriteTheRowsOfEveryTable() {
        List<String> tables = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                + "ORDER BY tablename", String.class);
        assertThat(tables).as("every table of the migrations").contains("invoice", "invoice_document", "expense",
                "business_document", "audit_event", "payment", "reminder", "flyway_schema_history");
        SoftAssertions softly = new SoftAssertions();
        for (String table : tables) {
            // TRUNCATE skips the row triggers that keep issued invoices and documents, so it must fail on its own.
            for (String sql : List.of("TRUNCATE " + table, "ALTER TABLE " + table + " DISABLE TRIGGER ALL",
                    "ALTER TABLE " + table + " ADD COLUMN probe text", "DROP TABLE " + table)) {
                refused(softly, sql);
            }
        }
        refused(softly, "CREATE TABLE probe (id int)");
        refused(softly, "CREATE FUNCTION probe() RETURNS int AS 'SELECT 1' LANGUAGE sql");
        softly.assertAll();
    }

    /** Expects the statement to fail. It runs in a transaction rolled back anyway, so a success harms no other test. */
    private void refused(SoftAssertions softly, String sql) {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        softly.assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.execute(sql);
        })).as(sql).isInstanceOf(DataAccessException.class);
    }

    @Test
    void aMemberOfTheOwnerWithoutItsPrivilegesIsRefusedToo() {
        JdbcClient asOwner = owner.jdbc();
        asOwner.sql("DROP ROLE IF EXISTS guard_probe").update();
        asOwner.sql("CREATE ROLE guard_probe LOGIN NOINHERIT PASSWORD 'probe'").update();
        asOwner.sql("GRANT \"" + owner.username() + "\" TO guard_probe").update();
        try {
            DriverManagerDataSource probe = owner.dataSource(null);
            probe.setUsername("guard_probe");
            probe.setPassword("probe");
            ConnectionGuard guard = new ConnectionGuard(JdbcClient.create(probe));
            assertThatThrownBy(() -> guard.run(null))
                    .as("SET ROLE would reach the triggers").isInstanceOf(IllegalStateException.class);
        } finally {
            asOwner.sql("DROP ROLE guard_probe").update();
        }
    }

    @Test
    void aMemberOfASuperuserWhoOwnsNoTableIsRefused() {
        JdbcClient asOwner = owner.jdbc();
        asOwner.sql("DROP ROLE IF EXISTS super_probe_login").update();
        asOwner.sql("DROP ROLE IF EXISTS super_probe").update();
        asOwner.sql("CREATE ROLE super_probe SUPERUSER NOLOGIN").update();
        asOwner.sql("CREATE ROLE super_probe_login LOGIN NOINHERIT PASSWORD 'probe'").update();
        asOwner.sql("GRANT super_probe TO super_probe_login").update();
        try {
            DriverManagerDataSource probe = owner.dataSource(null);
            probe.setUsername("super_probe_login");
            probe.setPassword("probe");
            ConnectionGuard guard = new ConnectionGuard(JdbcClient.create(probe));
            assertThatThrownBy(() -> guard.run(null)).as("SET ROLE super_probe would switch any trigger off")
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            asOwner.sql("DROP ROLE super_probe_login").update();
            asOwner.sql("DROP ROLE super_probe").update();
        }
    }

    private Invoice issued(LocalDate date) {
        SellerTestData.save(sellers, SellerTestData.profile());
        numbers.savePattern("{YYYY}-{NN}");
        Customer customer = customers.create(CustomerTestData.verlag());
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        form.setIssueDate(date);
        form.setServiceFrom(date);
        form.setServiceTo(date);
        form.setBuyerReference(customer.buyerReference());
        LineForm line = form.getLines().getFirst();
        line.setText("Lesung");
        line.setAmount("400");
        line.setCategory(TaxCategory.KLEINUNTERNEHMER);
        Invoice saved = invoices.saveDraft(draft.id(), form);
        issues.issue(saved.id(), saved.version());
        return invoices.find(saved.id()).orElseThrow();
    }

    /** Runs SQL as the owner with the triggers of the table off, the way someone with the owner's password could. */
    private void asOwner(String table, String trigger, String sql, Object... args) {
        JdbcTemplate ownerJdbc = new JdbcTemplate(owner.dataSource(null));
        ownerJdbc.execute("ALTER TABLE " + table + " DISABLE TRIGGER " + trigger);
        try {
            ownerJdbc.update(sql, args);
        } finally {
            ownerJdbc.execute("ALTER TABLE " + table + " ENABLE TRIGGER " + trigger);
        }
    }

    @Test
    void aChangedXmlShowsWithItsDifferencesAndStaysIn() {
        Invoice invoice = issued(LocalDate.of(2061, 3, 18));
        assertThat(integrity.check(invoice).intact()).isTrue();
        assertThat(monitor.check().damaged()).extracting(Damage::invoiceId).doesNotContain(invoice.id());
        byte[] original = documents.find(invoice.id(), DocumentKind.XML).orElseThrow().content();
        byte[] changed = new String(original, StandardCharsets.UTF_8).replace(">400.00<", ">4000.00<")
                .getBytes(StandardCharsets.UTF_8);
        asOwner("invoice_document", "invoice_document_frozen",
                "UPDATE invoice_document SET content = ?, sha256 = encode(sha256(?), 'hex') WHERE invoice_id = ? "
                        + "AND kind = 'XML'", changed, changed, invoice.id());
        try {
            InvoiceIntegrity.Report report = integrity.check(invoice);
            assertThat(report.problems()).contains("Die XML-Datei weicht vom Stand der Ausstellung ab.",
                    "Die XML in der PDF-Datei weicht von der XML-Datei ab.")
                    .anySatisfy(problem -> assertThat(problem).contains("Das Siegel passt nicht"));
            assertThat(report.differences()).contains(new Difference("Gesamtbetrag", "400,00 €", "4.000,00 €"));
            long id = invoice.id();
            assertThatThrownBy(() -> documents.find(id, DocumentKind.XML))
                    .isInstanceOf(InvoiceException.class);
            assertThat(documents.find(invoice.id(), DocumentKind.PDF)).as("the PDF itself is intact").isPresent();
            assertThat(monitor.check().damaged()).extracting(Damage::invoiceId).contains(invoice.id());
            assertThat(audit.history(invoice.id())).extracting(AuditEvent::action)
                    .contains(AuditAction.INTEGRITY_FAILED);
        } finally {
            asOwner("invoice_document", "invoice_document_frozen",
                    "UPDATE invoice_document SET content = ?, sha256 = encode(sha256(?), 'hex') WHERE invoice_id = ? "
                            + "AND kind = 'XML'", original, original, invoice.id());
        }
        assertThat(integrity.check(invoice).intact()).as("the original bytes match again").isTrue();
    }

    @Test
    void aFindingGoesIntoTheLogOnceAndAgainAfterARepair() {
        Invoice invoice = issued(LocalDate.of(2064, 3, 18));
        monitor.check();
        byte[] original = documents.find(invoice.id(), DocumentKind.XML).orElseThrow().content();
        byte[] changed = new String(original, StandardCharsets.UTF_8).replace(">400.00<", ">4000.00<")
                .getBytes(StandardCharsets.UTF_8);
        String update = "UPDATE invoice_document SET content = ?, sha256 = encode(sha256(?), 'hex') "
                + "WHERE invoice_id = ? AND kind = 'XML'";
        try {
            asOwner("invoice_document", "invoice_document_frozen", update, changed, changed, invoice.id());
            monitor.check();
            monitor.check();
            assertThat(failures(invoice)).as("the second check finds nothing new").isEqualTo(1);

            asOwner("invoice_document", "invoice_document_frozen", update, original, original, invoice.id());
            assertThat(monitor.check().damaged()).extracting(Damage::invoiceId).doesNotContain(invoice.id());
            asOwner("invoice_document", "invoice_document_frozen", update, changed, changed, invoice.id());
            assertThat(monitor.check().damaged()).extracting(Damage::invoiceId).contains(invoice.id());
            assertThat(failures(invoice)).as("changed again after the repair").isEqualTo(2);
        } finally {
            asOwner("invoice_document", "invoice_document_frozen", update, original, original, invoice.id());
        }
        assertThat(monitor.check().damaged()).extracting(Damage::invoiceId).doesNotContain(invoice.id());
    }

    private long failures(Invoice invoice) {
        return audit.history(invoice.id()).stream().filter(event -> event.action() == AuditAction.INTEGRITY_FAILED)
                .count();
    }

    @Test
    void aChangedSnapshotOrBusinessDocumentShows() {
        Invoice invoice = issued(LocalDate.of(2062, 3, 18));
        String snapshot = invoice.snapshot();
        asOwner("invoice", "invoice_frozen", "UPDATE invoice SET snapshot = replace(snapshot, '400', '4000') "
                + "WHERE id = ?", invoice.id());
        try {
            Invoice changed = invoices.find(invoice.id()).orElseThrow();
            assertThat(integrity.check(changed).problems()).contains("Der Stand der Ausstellung passt nicht zu seinem Siegel. "
                    + "Er wurde außerhalb der App geändert.");
        } finally {
            asOwner("invoice", "invoice_frozen", "UPDATE invoice SET snapshot = ? WHERE id = ?", snapshot,
                    invoice.id());
        }

        DocumentForm form = new DocumentForm();
        form.setCategory(DocumentCategory.CERTIFICATE);
        form.setTitle("Bescheinigung");
        form.setDocumentDate(LocalDate.of(2062, 1, 5));
        BusinessDocument added = businessDocuments.add(form, "Bescheinigung.pdf",
                "%PDF-1.4 echt".getBytes(StandardCharsets.US_ASCII));
        assertThat(businessDocuments.problems(added.id())).isEmpty();
        asOwner("business_document", "business_document_frozen",
                "UPDATE business_document SET content = ? WHERE id = ?",
                "%PDF-1.4 falsch".getBytes(StandardCharsets.US_ASCII), added.id());
        try {
            assertThat(businessDocuments.problems(added.id())).singleElement().asString()
                    .contains("Der Inhalt passt nicht zur gespeicherten Prüfsumme");
            long id = added.id();
            assertThatThrownBy(() -> businessDocuments.file(id)).isInstanceOf(DocumentException.class);
        } finally {
            asOwner("business_document", "business_document_frozen",
                    "UPDATE business_document SET content = ? WHERE id = ?",
                    "%PDF-1.4 echt".getBytes(StandardCharsets.US_ASCII), added.id());
        }
        assertThat(businessDocuments.file(added.id())).isPresent();
    }

    @Test
    void theMigrationSealsWhatExistsAndSwitchesTheTriggersBackOn() {
        Invoice invoice = issued(LocalDate.of(2063, 3, 18));
        String seal = jdbc.queryForObject("SELECT seal FROM invoice WHERE id = ?", String.class, invoice.id());
        sealMigration.migrate(new JdbcTemplate(owner.dataSource(null)));
        assertThat(jdbc.queryForObject("SELECT seal FROM invoice WHERE id = ?", String.class, invoice.id()))
                .isEqualTo(seal);
        assertThat(integrity.check(invoice).intact()).isTrue();
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET intro = 'neu' WHERE id = ?", invoice.id()))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice_document SET filename = 'x' WHERE invoice_id = ?",
                invoice.id())).hasMessageContaining("immutable");
        long id = invoice.id();
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET seal = NULL WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }
}
