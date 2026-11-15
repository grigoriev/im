package io.github.grigoriev.im.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.document.DocumentFile;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.Seal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/** The expenses with a fake table: one map of columns per row. */
class ExpenseServiceTest {

    /** 23:30 in Berlin on May 11, already the 12th: today comes from the zone of the clock. */
    private static final Instant NOW = Instant.parse("2027-05-11T22:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 5, 12);
    private static final byte[] PDF = "%PDF-1.7 Quittung".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AuditService audit = mock(AuditService.class);
    private final Seal seal = new Seal("test-only-integrity-key-0123456789abcdef");
    private final ExpenseService service = new ExpenseService(jdbc, audit, seal,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    private final Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    private StatementSpec statement(String sql) {
        StatementSpec spec = mock(StatementSpec.class);
        Map<String, Object> params = new HashMap<>();
        when(spec.param(anyString(), any())).thenAnswer(call -> {
            params.put(call.getArgument(0), call.getArgument(1));
            return spec;
        });
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<Object> mapper = call.getArgument(0);
            MappedQuerySpec<Object> query = mock(MappedQuerySpec.class);
            when(query.list()).thenAnswer(list -> select(sql, params, mapper));
            when(query.optional()).thenAnswer(one -> select(sql, params, mapper).stream().findFirst());
            return query;
        });
        MappedQuerySpec<Long> longs = mock(MappedQuerySpec.class);
        when(spec.query(Long.class)).thenReturn(longs);
        when(longs.single()).thenAnswer(call -> rows.size() + 1L);
        when(longs.list()).thenAnswer(call -> new ArrayList<>(rows.keySet()));
        MappedQuerySpec<Integer> ints = mock(MappedQuerySpec.class);
        when(spec.query(Integer.class)).thenReturn(ints);
        when(ints.list()).thenAnswer(call -> rows.values().stream()
                .map(row -> ((LocalDate) row.get("paid_on")).getYear()).distinct().toList());
        when(spec.update()).thenAnswer(call -> write(sql, params));
        return spec;
    }

    private List<Object> select(String sql, Map<String, Object> params, RowMapper<Object> mapper) throws SQLException {
        List<Object> result = new ArrayList<>();
        for (Map<String, Object> row : rows.values()) {
            boolean found = sql.equals(ExpenseService.OF_YEAR)
                    ? !((LocalDate) row.get("paid_on")).isBefore((LocalDate) params.get("from"))
                            && !((LocalDate) row.get("paid_on")).isAfter((LocalDate) params.get("to"))
                    : row.get("id").equals(params.get("id"));
            if (found) {
                result.add(mapper.mapRow(resultSet(row), 0));
            }
        }
        return result;
    }

    private static ResultSet resultSet(Map<String, Object> row) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(anyString())).thenAnswer(call -> (Long) row.get(call.getArgument(0)));
        when(rs.getInt(anyString())).thenAnswer(call -> (Integer) row.get(call.getArgument(0)));
        when(rs.getString(anyString())).thenAnswer(call -> (String) row.get(call.getArgument(0)));
        when(rs.getBoolean(anyString())).thenAnswer(call -> (Boolean) row.get(call.getArgument(0)));
        when(rs.getBigDecimal(anyString())).thenAnswer(call -> (BigDecimal) row.get(call.getArgument(0)));
        when(rs.getObject(anyString(), any(Class.class))).thenAnswer(call -> row.get(call.getArgument(0)));
        when(rs.getTimestamp(anyString())).thenAnswer(call -> (Timestamp) row.get(call.getArgument(0)));
        List<String> file = List.of("filename", "media_type", "content", "sha256", "seal");
        when(rs.getString(anyInt())).thenAnswer(call -> (String) row.get(file.get((int) call.getArgument(0) - 1)));
        when(rs.getBytes(anyInt())).thenAnswer(call -> (byte[]) row.get(file.get((int) call.getArgument(0) - 1)));
        return rs;
    }

    private int write(String sql, Map<String, Object> params) {
        if (sql.equals(ExpenseService.INSERT)) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", params.get("id"));
            row.put("filename", params.get("filename"));
            row.put("media_type", params.get("mediaType"));
            row.put("content", params.get("content"));
            row.put("sha256", params.get("sha256"));
            row.put("size", params.get("size"));
            row.put("seal", params.get("seal"));
            row.put("created_at", params.get("createdAt"));
            row.put("created_by", params.get("createdBy"));
            details(row, params);
            rows.put((Long) params.get("id"), row);
            return 1;
        }
        Map<String, Object> row = rows.get((Long) params.get("id"));
        if (sql.equals(ExpenseService.CANCEL)) {
            row.put("cancelled_at", params.get("at"));
            row.put("cancel_reason", params.get("reason"));
        } else {
            details(row, params);
        }
        return 1;
    }

    private static void details(Map<String, Object> row, Map<String, Object> params) {
        row.put("paid_on", params.get("paidOn"));
        row.put("amount", params.get("amount"));
        row.put("category", params.get("category"));
        row.put("vendor", params.get("vendor"));
        row.put("description", params.get("description"));
        row.put("asset", params.get("asset"));
    }

    @BeforeEach
    void table() {
        when(jdbc.sql(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
    }

    private static ExpenseForm form(LocalDate paidOn, String amount, ExpenseCategory category, String vendor,
            String description, boolean asset) {
        ExpenseForm form = new ExpenseForm();
        form.setPaidOn(paidOn);
        form.setAmount(amount);
        form.setCategory(category);
        form.setVendor(vendor);
        form.setDescription(description);
        form.setAsset(asset);
        return form;
    }

    private static ExpenseForm book(LocalDate paidOn) {
        return form(paidOn, "49,9", ExpenseCategory.LITERATURE, " Buchhandlung am Markt ", " Wörterbuch ", false);
    }

    @Test
    void entersAnExpenseWithItsSealedReceipt() {
        Expense added = service.add(book(TODAY), "C:\\Belege\\Quittung.pdf", PDF);
        assertThat(added).isEqualTo(new Expense(1, TODAY, new BigDecimal("49.90"), ExpenseCategory.LITERATURE,
                "Buchhandlung am Markt", "Wörterbuch", false, null, null, "Quittung.pdf", "application/pdf",
                Seal.sha256(PDF), PDF.length, NOW, "system"));
        assertThat(added.cancelled()).isFalse();
        assertThat(rows.get(1L)).containsEntry("seal", seal.expense(1, Seal.sha256(PDF)));
        assertThat(service.file(1)).contains(new DocumentFile("Quittung.pdf", "application/pdf", PDF));
        assertThat(service.problems(1)).isEmpty();
        assertThat(service.damaged()).isEmpty();
        verify(audit).add(AuditAction.EXPENSE_ADDED, "12.05.2027, 49,90 €, Fachliteratur, Buchhandlung am Markt: "
                + "Wörterbuch");
        assertThat(service.today()).isEqualTo(TODAY);
    }

    @Test
    void listsTheExpensesOfAYearAndSumsTheValidOnesByKind() {
        service.add(book(LocalDate.of(2026, 12, 31)), "a.pdf", PDF);
        service.add(book(LocalDate.of(2027, 1, 1)), "b.pdf", PDF);
        service.add(form(LocalDate.of(2027, 1, 2), "1.299,00", ExpenseCategory.WORK_EQUIPMENT, "Laden", "Laptop", true),
                "c.png", PNG);
        service.add(book(TODAY), "d.pdf", PDF);
        service.cancel(4, "doppelt");
        assertThat(service.ofYear(2026)).extracting(Expense::id).containsExactly(1L);
        List<Expense> year = service.ofYear(2027);
        assertThat(year).extracting(Expense::id).containsExactly(2L, 3L, 4L);
        assertThat(ExpenseService.totals(year)).containsExactly(
                Map.entry(ExpenseCategory.WORK_EQUIPMENT, new BigDecimal("1299.00")),
                Map.entry(ExpenseCategory.LITERATURE, new BigDecimal("49.90")));
        assertThat(service.years()).containsExactly(2026, 2027);
        assertThat(year.get(1).asset()).isTrue();
        assertThat(year.get(1).mediaType()).isEqualTo("image/png");
    }

    @Test
    void changesTheDetailsAndLogsBeforeAndAfter() {
        service.add(book(TODAY), "a.pdf", PDF);
        Expense changed = service.change(1, form(TODAY.minusDays(1), "59,90", ExpenseCategory.TRAINING, "Akademie",
                "Kurs", true));
        assertThat(changed.amount()).isEqualByComparingTo("59.90");
        assertThat(changed.category()).isEqualTo(ExpenseCategory.TRAINING);
        assertThat(changed.asset()).isTrue();
        assertThat(changed.sha256()).as("the receipt stays").isEqualTo(Seal.sha256(PDF));
        verify(audit).add(AuditAction.EXPENSE_CHANGED, "12.05.2027, 49,90 €, Fachliteratur, Buchhandlung am Markt: "
                + "Wörterbuch → 11.05.2027, 59,90 €, Fortbildung, Akademie: Kurs, Anlagegut");
        assertThatThrownBy(() -> service.change(9, book(TODAY))).isInstanceOf(ExpenseException.class)
                .hasMessage(ExpenseService.UNKNOWN);
    }

    @Test
    void cancelsWithAReasonAndKeepsItFromThenOn() {
        service.add(book(TODAY), "a.pdf", PDF);
        assertThat(problems(() -> service.cancel(1, "  "))).containsExactly("Bitte den Grund angeben.");
        assertThat(problems(() -> service.cancel(1, "x".repeat(501)))).as("500 characters fit").containsExactly("Der Grund hat höchstens 500 "
                + "Zeichen.");
        service.add(book(TODAY), "b.pdf", PDF);
        assertThat(service.cancel(2, "x".repeat(500)).cancelReason()).hasSize(500);
        Expense cancelled = service.cancel(1, " doppelt erfasst ");
        assertThat(cancelled.cancelled()).isTrue();
        assertThat(cancelled.cancelReason()).isEqualTo("doppelt erfasst");
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
        verify(audit).add(AuditAction.EXPENSE_CANCELLED, "12.05.2027, 49,90 €, Fachliteratur, Buchhandlung am Markt: "
                + "Wörterbuch, Grund: doppelt erfasst");
        assertThat(problems(() -> service.cancel(1, "nochmal"))).containsExactly(ExpenseService.CANCELLED);
        assertThat(problems(() -> service.change(1, book(TODAY)))).containsExactly(ExpenseService.CANCELLED);
    }

    private static List<String> problems(Runnable action) {
        try {
            action.run();
            return List.of();
        } catch (ExpenseException e) {
            return e.problems();
        }
    }

    @Test
    void refusesWrongValuesAndAMissingReceipt() {
        assertThat(problems(() -> service.add(form(null, null, null, null, null, false), "a.pdf", null)))
                .containsExactly("Bitte eine Datei wählen.", "Bitte den Tag der Zahlung angeben.",
                        "Bitte den Betrag angeben.", "Bitte die Art wählen.", "Bitte Empfänger angeben.",
                        "Bitte Beschreibung angeben.");
        assertThat(problems(() -> service.add(form(TODAY.plusDays(1), "1.500", ExpenseCategory.OTHER, "x".repeat(201),
                "y".repeat(501), false), "a.pdf", PDF))).containsExactly("Der Tag der Zahlung liegt in der Zukunft.",
                "Der Betrag ist mehrdeutig: bitte mit Komma schreiben, zum Beispiel 1.500,00.",
                "Empfänger hat höchstens 200 Zeichen.", "Beschreibung hat höchstens 500 Zeichen.");
        assertThat(problems(() -> service.add(form(TODAY, "zehn", ExpenseCategory.OTHER, "a", "b", false), "a.pdf",
                PDF))).containsExactly("Der Betrag ist keine Zahl, zum Beispiel 49,90.");
        assertThat(problems(() -> service.add(form(TODAY, "0", ExpenseCategory.OTHER, "a", "b", false), "a.pdf", PDF)))
                .containsExactly("Der Betrag ist größer als 0.");
        assertThat(problems(() -> service.add(form(TODAY, "-5", ExpenseCategory.OTHER, "a", "b", false), "a.pdf",
                PDF))).containsExactly("Der Betrag ist größer als 0.");
        assertThat(problems(() -> service.add(form(TODAY, "12345678901", ExpenseCategory.OTHER, "a", "b", false),
                "a.pdf", PDF))).containsExactly("Der Betrag hat höchstens 10 Stellen vor dem Komma.");
        assertThat(problems(() -> service.add(form(TODAY, "1,234", ExpenseCategory.OTHER, "a", "b", false), "a.pdf",
                PDF))).containsExactly("Der Betrag hat höchstens 2 Nachkommastellen.");
        assertThat(problems(() -> service.add(form(TODAY, "   ", ExpenseCategory.OTHER, "a", "b", false), "a.pdf",
                PDF))).containsExactly("Bitte den Betrag angeben.");
        assertThat(service.add(form(TODAY, "1234567890,10", ExpenseCategory.OTHER, "a", "b", false), "a.pdf", PDF)
                .amount()).isEqualByComparingTo("1234567890.10");
        assertThat(service.add(form(TODAY, "2,50", ExpenseCategory.OTHER, "a", "b", false), "a.pdf", PDF).amount())
                .isEqualByComparingTo("2.50");
        assertThat(service.add(form(TODAY, "2,55", ExpenseCategory.OTHER, "a", "b", false), "a.pdf", PDF).amount())
                .as("two decimals fit").isEqualByComparingTo("2.55");
        verify(audit, never()).add(AuditAction.EXPENSE_CHANGED, "");
    }

    @Test
    void findsAReceiptChangedInTheDatabaseAndRefusesToHandItOut() {
        service.add(book(TODAY), "Quittung.pdf", PDF);
        service.add(book(TODAY), "Zweite.pdf", PDF);
        String sha = Seal.sha256(PDF);
        rows.get(1L).put("content", PNG);
        assertThat(service.problems(1)).containsExactly("Quittung.pdf: Der Inhalt passt nicht zur gespeicherten "
                + "Prüfsumme. Gespeichert: " + sha + ", berechnet: " + Seal.sha256(PNG) + ".");
        assertThatThrownBy(() -> service.file(1)).isInstanceOf(ExpenseException.class).hasMessage("Der Beleg "
                + "Quittung.pdf ist beschädigt oder wurde außerhalb der App geändert. Die Seite der Ausgabe zeigt, was "
                + "abweicht.");
        rows.get(1L).put("sha256", Seal.sha256(PNG));
        assertThat(service.problems(1)).containsExactly("Quittung.pdf: Das Siegel passt nicht. Prüfsumme oder "
                + "Zuordnung der Datei wurden außerhalb der App geändert.");
        rows.get(2L).put("seal", rows.get(1L).get("seal"));
        assertThat(service.damaged()).extracting(Damage::what, Damage::href, Damage::invoiceId).containsExactly(
                tuple("Beleg Quittung.pdf", "/expenses/1", null), tuple("Beleg Zweite.pdf", "/expenses/2", null));
        assertThat(service.problems(9)).isEmpty();
        assertThat(service.file(9)).isEmpty();
        assertThat(service.find(9)).isEmpty();
    }

    @Test
    void fillsTheFormAndFormatsEuros() {
        Expense expense = service.add(book(TODAY), "a.pdf", PDF);
        ExpenseForm form = ExpenseForm.of(expense);
        assertThat(form.getPaidOn()).isEqualTo(TODAY);
        assertThat(form.getAmount()).isEqualTo("49,90");
        assertThat(form.getCategory()).isEqualTo(ExpenseCategory.LITERATURE);
        assertThat(form.getVendor()).isEqualTo("Buchhandlung am Markt");
        assertThat(form.getDescription()).isEqualTo("Wörterbuch");
        assertThat(form.isAsset()).isFalse();
        assertThat(ExpenseService.euros(new BigDecimal("1234.5"))).isEqualTo("1.234,50 €");
        assertThat(ExpenseCategory.ADVICE.label()).isEqualTo("Rechts- und Steuerberatung");
        assertThat(new ExpenseException(List.of("a", "b"))).hasMessage("a b");
    }
}
