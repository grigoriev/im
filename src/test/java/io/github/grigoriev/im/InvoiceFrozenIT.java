package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the database keeps of an issued invoice, as the app role sees it: every column but the status and the
 * bookkeeping of a row, and a status that moves only along the graph of docs/PLAN.md, section 6. Each test uses its
 * own year.
 */
@SpringBootTest
@Import(TestDatabase.class)
class InvoiceFrozenIT {

    /** The columns an issued invoice may change: the status, the bookkeeping, and a deleted template. */
    private static final Set<String> MOVABLE = Set.of("status", "updated_at", "version", "template_id");

    private static final String COLUMNS = """
            SELECT column_name, data_type, is_identity FROM information_schema.columns
             WHERE table_schema = current_schema() AND table_name = 'invoice' ORDER BY ordinal_position""";

    @Autowired
    private IssueService issues;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private NumberService numbers;

    @Autowired
    private JdbcTemplate jdbc;

    private Customer verlag;

    @BeforeEach
    void profileAndCustomer() {
        SellerTestData.save(sellers, SellerTestData.profile());
        verlag = customers.create(CustomerTestData.verlag());
        numbers.savePattern("{YYYY}-{NN}");
    }

    /** A complete draft dated on a day. */
    private Invoice draft(LocalDate date) {
        Invoice draft = invoices.createDraft(verlag.id(), null);
        DraftForm form = DraftForm.of(draft);
        form.setIssueDate(date);
        form.setServiceFrom(date);
        form.setServiceTo(date);
        form.setBuyerReference(verlag.buyerReference());
        LineForm line = form.getLines().getFirst();
        line.setText("Lesung");
        line.setLumpSum(true);
        line.setAmount("400");
        line.setCategory(TaxCategory.KLEINUNTERNEHMER);
        return invoices.saveDraft(draft.id(), form);
    }

    private long issued(LocalDate date) {
        Invoice draft = draft(date);
        issues.issue(draft.id(), draft.version());
        return draft.id();
    }

    private int move(long id, String status) {
        return jdbc.update("UPDATE invoice SET status = ?, version = version + 1 WHERE id = ?", status, id);
    }

    private void refused(long id, String status) {
        assertThatThrownBy(() -> move(id, status)).as("to " + status).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot move from");
    }

    @Test
    void theStatusMovesOnlyAlongTheGraph() {
        long id = issued(LocalDate.of(2071, 2, 1));
        refused(id, "DRAFT");
        assertThat(move(id, "PAID")).as("paid without a dispatch").isEqualTo(1);
        assertThat(move(id, "ISSUED")).as("a refund before any dispatch").isEqualTo(1);
        assertThat(move(id, "SENT")).isEqualTo(1);
        refused(id, "ISSUED");
        refused(id, "DRAFT");
        assertThat(move(id, "PAID")).isEqualTo(1);
        assertThat(move(id, "SENT")).as("a refund after a dispatch").isEqualTo(1);
        assertThat(move(id, "CANCELLED")).isEqualTo(1);
        for (String status : List.of("DRAFT", "ISSUED", "SENT", "PAID")) {
            refused(id, status);
        }

        long paid = issued(LocalDate.of(2071, 2, 2));
        assertThat(move(paid, "PAID")).isEqualTo(1);
        assertThat(move(paid, "CANCELLED")).as("a paid invoice can be cancelled").isEqualTo(1);
        long fresh = issued(LocalDate.of(2071, 2, 3));
        assertThat(move(fresh, "CANCELLED")).isEqualTo(1);
    }

    @Test
    void aDraftOnlyBecomesIssued() {
        long id = draft(LocalDate.of(2072, 3, 1)).id();
        for (String status : List.of("SENT", "PAID", "CANCELLED")) {
            refused(id, status);
        }
        assertThat(invoices.find(id).orElseThrow().isDraft()).isTrue();
    }

    @Test
    void everyColumnButTheStatusAndTheBookkeepingStaysAfterTheIssue() {
        long id = issued(LocalDate.of(2073, 4, 1));
        List<Map<String, Object>> columns = jdbc.queryForList(COLUMNS);
        assertThat(columns).extracting(column -> column.get("column_name")).containsAll(MOVABLE).contains("id",
                "number", "snapshot", "seal", "created_at");
        for (Map<String, Object> column : columns) {
            String name = (String) column.get("column_name");
            if (MOVABLE.contains(name)) {
                continue;
            }
            String change = "YES".equals(column.get("is_identity")) ? "DEFAULT"
                    : "CASE WHEN " + name + " IS NULL THEN " + value((String) column.get("data_type")) + " END";
            assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET " + name + " = " + change + " WHERE id = ?", id))
                    .as(name).isInstanceOf(DataAccessException.class).hasMessageContaining("is immutable");
        }
        assertThat(jdbc.update("UPDATE invoice SET template_id = NULL, updated_at = now(), version = version + 1 "
                + "WHERE id = ?", id)).as("the bookkeeping moves on").isEqualTo(1);
    }

    /** A value of a type that differs from NULL; a new column of another type needs its value here. */
    private static String value(String type) {
        return switch (type) {
            case "character varying", "character", "text" -> "'x'";
            case "date" -> "DATE '2000-01-01'";
            case "integer", "bigint" -> "1";
            case "timestamp with time zone" -> "now()";
            default -> throw new IllegalStateException("no test value for type " + type);
        };
    }
}
