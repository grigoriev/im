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
import io.github.grigoriev.im.einvoice.TaxCategory;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
    private DataSource dataSource;

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
    }

    @Test
    void anExportReadsBackIntoAnEmptyDatabaseExactly() throws IOException {
        someOfEverything();
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        backups.export(original);

        jdbc.execute("DROP DATABASE IF EXISTS im_copy");
        jdbc.execute("CREATE DATABASE im_copy");
        DataSource copy = copySource();
        Flyway.configure().dataSource(copy).locations("classpath:db/migration").load().migrate();
        BackupService into = new BackupService(JdbcClient.create(copy), json, mock(AuditService.class),
                Clock.systemDefaultZone());
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(copy));
        JdbcTemplate copyJdbc = new JdbcTemplate(copy);
        copyJdbc.update("INSERT INTO app_user (username, display_name, password_hash, role, enabled) "
                + "VALUES ('erster', 'Erster Admin', 'x', 'ADMIN', TRUE)");

        transaction.executeWithoutResult(status -> into.restore(new ByteArrayInputStream(original.toByteArray())));

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

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> into.restore(
                new ByteArrayInputStream(original.toByteArray()))))
                .isInstanceOf(BackupException.class).hasMessageContaining("schon Daten");
        assertThat(Arrays.equals(before.get("data/invoice.json"), after.get("data/invoice.json"))).isTrue();
    }

    /** The same server as the test database, with the database im_copy. */
    private DataSource copySource() {
        com.zaxxer.hikari.HikariDataSource main = (com.zaxxer.hikari.HikariDataSource) dataSource;
        DriverManagerDataSource copy = new DriverManagerDataSource(main.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/im_copy$1"));
        copy.setUsername(main.getUsername());
        copy.setPassword(main.getPassword());
        return copy;
    }
}
