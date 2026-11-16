package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.integrity.IntegrityMonitor;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceIntegrity;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The update of a database with data, as on the first installation: an issued invoice and a business document written
 * before the seals (version 27) are sealed by the migrations of the app, pass every check and stay frozen.
 */
@SpringBootTest
@Import(TestDatabase.class)
class MigrationIT {

    private static final String DATABASE = "im_upgrade";
    private static final String KEY = "test-only-integrity-key-0123456789abcdef";

    @Autowired
    private TestDatabase.Owner owner;

    @Autowired
    private JdbcTemplate jdbc;

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
    private DocumentService documents;

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

    /**
     * Copies rows of the test database into the old one, as they were written. The columns of later versions drop out,
     * and the triggers and foreign keys stay off, as for rows written by the old app itself.
     */
    private void copy(JdbcTemplate old, String table, String where, Object id) {
        List<String> rows = jdbc.queryForList("SELECT row_to_json(t)::text FROM " + table + " t WHERE " + where,
                String.class, id);
        assertThat(rows).as(table).isNotEmpty();
        for (String row : rows) {
            old.update("INSERT INTO " + table + " OVERRIDING SYSTEM VALUE SELECT * FROM json_populate_record(NULL::"
                    + table + ", ?::json)", row);
        }
    }

    @Test
    void sealsWhatAnOlderVersionWroteAndChecksItAtStartup() throws SQLException {
        Invoice invoice = issued(LocalDate.of(2067, 3, 18));
        DocumentForm letter = new DocumentForm();
        letter.setCategory(DocumentCategory.TAX_OFFICE);
        letter.setTitle("Steuernummer");
        letter.setDocumentDate(LocalDate.of(2067, 1, 5));
        BusinessDocument document = documents.add(letter, "Brief.pdf",
                "%PDF-1.4 Brief".getBytes(StandardCharsets.US_ASCII));

        owner.jdbc().sql("DROP DATABASE IF EXISTS " + DATABASE).update();
        owner.jdbc().sql("CREATE DATABASE " + DATABASE).update();
        DriverManagerDataSource oldOwner = owner.dataSource(DATABASE);
        Flyway.configure().dataSource(oldOwner).locations("classpath:db/migration").target("27").load().migrate();
        try (Connection connection = oldOwner.getConnection()) {
            JdbcTemplate old = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertThat(old.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = "
                    + "'invoice'", String.class)).as("a database before the seals").doesNotContain("seal");
            old.execute("SET session_replication_role = replica");
            copy(old, "customer", "id = ?", invoice.customerId());
            copy(old, "invoice", "id = ?", invoice.id());
            copy(old, "invoice_line", "invoice_id = ?", invoice.id());
            copy(old, "invoice_document", "invoice_id = ?", invoice.id());
            copy(old, "business_document", "id = ?", document.id());
        }

        try (Installation app = Installation.start(owner, DATABASE, KEY)) {
            IntegrityMonitor monitor = app.bean(IntegrityMonitor.class);
            assertThat(monitor.last()).as("the check at startup ran").hasValueSatisfying(result ->
                    assertThat(result.damaged()).isEmpty());
            Invoice migrated = app.bean(InvoiceService.class).find(invoice.id()).orElseThrow();
            assertThat(app.bean(InvoiceIntegrity.class).check(migrated).problems()).isEmpty();
            assertThat(app.bean(DocumentService.class).file(document.id())).isPresent();
            JdbcTemplate upgraded = app.bean(JdbcTemplate.class);
            assertThat(upgraded.queryForObject("SELECT count(*) FROM invoice_document WHERE invoice_id = ? AND seal "
                    + "IS NOT NULL", Integer.class, invoice.id())).as("PDF and XML").isEqualTo(2);
            assertThat(upgraded.queryForObject("SELECT seal FROM invoice WHERE id = ?", String.class, invoice.id()))
                    .isNotNull();
            assertThatThrownBy(() -> upgraded.update("UPDATE invoice SET intro = 'neu' WHERE id = ?", invoice.id()))
                    .as("the triggers are on again").hasMessageContaining("immutable");
            assertThatThrownBy(() -> upgraded.update("DELETE FROM business_document"))
                    .isInstanceOf(DataAccessException.class);
            assertThat(upgraded.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY "
                    + "installed_rank DESC LIMIT 1", String.class)).as("the latest version")
                    .containsExactly(latest());
        }
    }

    private String latest() {
        return jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC "
                + "LIMIT 1", String.class);
    }
}
