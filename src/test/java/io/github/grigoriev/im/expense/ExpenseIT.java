package io.github.grigoriev.im.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.Parallel;
import io.github.grigoriev.im.TestDatabase;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.IntegrityMonitor;
import io.github.grigoriev.im.web.ReportController;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private ReportController reports;

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

    @Test
    void parallelChangesAndCancellationsWaitForEachOther() throws Exception {
        Expense added = expenses.add(form(LocalDate.of(2017, 2, 1)), "Quittung.pdf", PDF);
        long id = added.id();
        List<Callable<Expense>> calls = new ArrayList<>();
        for (int n = 0; n < 8; n++) {
            ExpenseForm changed = form(LocalDate.of(2017, 2, 1));
            changed.setAmount((10 + n) + ",00");
            calls.add(() -> expenses.change(id, changed));
            calls.add(() -> expenses.cancel(id, "doppelt erfasst"));
        }
        List<Object> outcomes = Parallel.run(calls);

        assertThat(outcomes).as("a refusal comes from the service, never from the trigger of the database")
                .allSatisfy(outcome -> assertThat(outcome).isInstanceOfAny(Expense.class, ExpenseException.class));
        assertThat(outcomes).filteredOn(Expense.class::isInstance).map(Expense.class::cast)
                .as("one cancellation, and changes only before it").filteredOn(Expense::cancelled).hasSize(1);
        assertThat(outcomes).filteredOn(ExpenseException.class::isInstance).map(ExpenseException.class::cast)
                .allSatisfy(refused -> assertThat(refused).hasMessage(ExpenseService.CANCELLED));
        Expense after = expenses.find(id).orElseThrow();
        assertThat(after.cancelled()).isTrue();
        assertThat(after.amount()).as("the last change before the cancellation stays")
                .isEqualByComparingTo(outcomes.stream().filter(Expense.class::isInstance).map(Expense.class::cast)
                        .filter(Expense::cancelled).findFirst().orElseThrow().amount());
    }

    @Test
    void theCsvForWisoHoldsTheValidExpensesOfTheYearWithTheAssetsMarked() {
        LocalDate day = LocalDate.of(2007, 3, 5);
        Expense book = expenses.add(form(day), "Buch.pdf", PDF);
        ExpenseForm stamps = form(day.plusDays(1));
        stamps.setAmount("10");
        stamps.setCategory(ExpenseCategory.OFFICE);
        stamps.setVendor("=Post");
        stamps.setDescription("Briefmarken; 10 Stück");
        expenses.add(stamps, "Marken.pdf", PDF);
        ExpenseForm laptop = form(day.plusDays(2));
        laptop.setAmount("899,00");
        laptop.setCategory(ExpenseCategory.WORK_EQUIPMENT);
        laptop.setDescription("Laptop");
        laptop.setAsset(true);
        expenses.add(laptop, "Laptop.pdf", PDF);
        Expense twice = expenses.add(form(day), "Buch.pdf", PDF);
        expenses.cancel(twice.id(), "doppelt erfasst");
        expenses.add(form(LocalDate.of(2008, 1, 1)), "Buch.pdf", PDF);

        ResponseEntity<byte[]> csv = reports.expensesCsv(2007);

        assertThat(csv.getHeaders().getContentDisposition().getFilename()).isEqualTo("Ausgaben_2007.csv");
        assertThat(new String(csv.getBody(), StandardCharsets.UTF_8).split("\r\n")).containsExactly(
                "\uFEFF" + "Zahlung;Betrag;Art;Empfänger;Beschreibung;Anlagegut;Beleg;SHA-256",
                "05.03.2007;49,90;\"Fachliteratur\";\"Buchhandlung am Markt\";\"Wörterbuch\";nein;\"Buch.pdf\";"
                        + book.sha256(),
                "06.03.2007;10,00;\"Bürobedarf und Porto\";\"'=Post\";\"Briefmarken; 10 Stück\";nein;\"Marken.pdf\";"
                        + book.sha256(),
                "07.03.2007;899,00;\"Arbeitsmittel\";\"Buchhandlung am Markt\";\"Laptop\";ja;\"Laptop.pdf\";"
                        + book.sha256());
        List<Expense> ofYear = expenses.ofYear(2007);
        assertThat(ExpenseService.totals(ofYear)).as("the sums by kind leave the asset and the cancellation out")
                .containsOnly(Map.entry(ExpenseCategory.LITERATURE, new BigDecimal("49.90")),
                        Map.entry(ExpenseCategory.OFFICE, new BigDecimal("10.00")));
        assertThat(ExpenseService.assets(ofYear)).extracting(Expense::description).containsExactly("Laptop");
    }
}
