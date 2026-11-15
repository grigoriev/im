package io.github.grigoriev.im.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.TestDatabase;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.IntegrityMonitor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The expenses against PostgreSQL: the receipt is written once and sealed, an expense never goes away, a cancelled one
 * stays as it is, and a receipt changed by the owner shows. Each test uses its own year.
 */
@SpringBootTest
@Import(TestDatabase.class)
class ExpenseIT {

    private static final byte[] PDF = "%PDF-1.4 Quittung".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private ExpenseService expenses;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestDatabase.Owner owner;

    @Autowired
    private IntegrityMonitor monitor;

    private static ExpenseForm form(LocalDate paidOn) {
        ExpenseForm form = new ExpenseForm();
        form.setPaidOn(paidOn);
        form.setAmount("49,90");
        form.setCategory(ExpenseCategory.LITERATURE);
        form.setVendor("Buchhandlung am Markt");
        form.setDescription("Wörterbuch");
        return form;
    }

    @Test
    void keepsAnExpenseWithItsReceiptAndCountsItInTheYearOfPayment() {
        Expense added = expenses.add(form(LocalDate.of(2019, 12, 31)), "Quittung.pdf", PDF);
        assertThat(expenses.ofYear(2019)).extracting(Expense::id).containsExactly(added.id());
        assertThat(expenses.ofYear(2020)).isEmpty();
        assertThat(expenses.years()).contains(2019);
        assertThat(expenses.file(added.id()).orElseThrow().content()).isEqualTo(PDF);
        ExpenseForm changed = form(LocalDate.of(2019, 12, 30));
        changed.setAmount("59,90");
        assertThat(expenses.change(added.id(), changed).amount()).isEqualByComparingTo("59.90");
        assertThat(ExpenseService.totals(expenses.ofYear(2019))).as("counts before the cancellation")
                .containsEntry(ExpenseCategory.LITERATURE, new BigDecimal("59.90"));

        long id = added.id();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM expense WHERE id = ?", id)).hasMessageContaining("is kept");
        assertThatThrownBy(() -> jdbc.update("UPDATE expense SET content = ? WHERE id = ?", new byte[] {1}, id))
                .hasMessageContaining("immutable");
        expenses.cancel(id, "doppelt erfasst");
        assertThat(ExpenseService.totals(expenses.ofYear(2019))).isEmpty();
        assertThatThrownBy(() -> jdbc.update("UPDATE expense SET amount = 1 WHERE id = ?", id))
                .hasMessageContaining("cancelled expense");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO expense (paid_on, amount, category, vendor, description, "
                + "asset, filename, media_type, content, sha256, size, seal, created_at, created_by) VALUES "
                + "('2019-01-01', 0, 'OTHER', 'a', 'b', FALSE, 'a.pdf', 'application/pdf', '\\\\x00', 'x', 1, 'x', now(), "
                + "'x')")).as("no amount of zero").hasMessageContaining("check constraint");
    }

    @Test
    void aReceiptChangedByTheOwnerShows() {
        Expense added = expenses.add(form(LocalDate.of(2018, 6, 1)), "Beleg.pdf", PDF);
        JdbcTemplate asOwner = new JdbcTemplate(owner.dataSource(null));
        asOwner.execute("ALTER TABLE expense DISABLE TRIGGER expense_frozen");
        try {
            asOwner.update("UPDATE expense SET content = ?, sha256 = encode(sha256(?), 'hex') WHERE id = ?",
                    "%PDF-1.4 falsch".getBytes(StandardCharsets.US_ASCII),
                    "%PDF-1.4 falsch".getBytes(StandardCharsets.US_ASCII), added.id());
        } finally {
            asOwner.execute("ALTER TABLE expense ENABLE TRIGGER expense_frozen");
        }
        long id = added.id();
        try {
            assertThat(expenses.problems(id)).singleElement().asString().contains("Das Siegel passt nicht");
            assertThatThrownBy(() -> expenses.file(id)).isInstanceOf(ExpenseException.class);
            assertThat(monitor.check().damaged()).extracting(Damage::href).contains("/expenses/" + id);
        } finally {
            asOwner.execute("ALTER TABLE expense DISABLE TRIGGER expense_frozen");
            asOwner.update("UPDATE expense SET content = ?, sha256 = encode(sha256(?), 'hex') WHERE id = ?", PDF, PDF,
                    id);
            asOwner.execute("ALTER TABLE expense ENABLE TRIGGER expense_frozen");
        }
        assertThat(expenses.problems(id)).isEmpty();
    }
}
