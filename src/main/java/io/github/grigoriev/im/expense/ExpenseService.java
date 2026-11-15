package io.github.grigoriev.im.expense;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.document.DocumentFile;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.IntegrityCheck;
import io.github.grigoriev.im.integrity.Seal;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.validation.Fields;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the business expenses with their receipts (docs/PLAN.md, section 7, "Expenses"). An expense counts in the year
 * of its payment. Its receipt is written once and sealed; every read checks it. A wrong expense is cancelled with a
 * reason, never deleted. Every entry, change and cancellation goes into the audit log.
 */
@Service
public class ExpenseService implements IntegrityCheck {

    static final int MAX_VENDOR = 200;
    static final int MAX_DESCRIPTION = 500;
    static final int MAX_REASON = 500;
    static final int MAX_INTEGER_DIGITS = 10;
    static final String UNKNOWN = "Diese Ausgabe gibt es nicht.";
    static final String CANCELLED = "Eine stornierte Ausgabe ändert sich nicht mehr.";
    static final String DAMAGED = "Der Beleg %s ist beschädigt oder wurde außerhalb der App geändert. Die Seite der "
            + "Ausgabe zeigt, was abweicht.";
    static final String WRONG_SHA = "%s: Der Inhalt passt nicht zur gespeicherten Prüfsumme. Gespeichert: %s, "
            + "berechnet: %s.";
    static final String WRONG_SEAL = "%s: Das Siegel passt nicht. Prüfsumme oder Zuordnung der Datei wurden außerhalb "
            + "der App geändert.";

    static final String COLUMNS = """
            id, paid_on, amount, category, vendor, description, asset, cancelled_at, cancel_reason, filename,
            media_type, sha256, size, created_at, created_by""";
    static final String OF_YEAR = "SELECT " + COLUMNS + " FROM expense WHERE paid_on BETWEEN :from AND :to "
            + "ORDER BY paid_on, id";
    static final String ONE = "SELECT " + COLUMNS + " FROM expense WHERE id = :id";
    /** A change or cancellation locks the row first: a change in another window waits, the log shows the true before. */
    static final String LOCKED = ONE + " FOR UPDATE";
    static final String YEARS = "SELECT DISTINCT CAST(EXTRACT(YEAR FROM paid_on) AS INTEGER) FROM expense";
    static final String IDS = "SELECT id FROM expense ORDER BY id";
    static final String FILE = "SELECT filename, media_type, content, sha256, seal FROM expense WHERE id = :id";
    /** The ID comes first: the seal names it. */
    static final String NEXT_ID = "SELECT nextval(pg_get_serial_sequence('expense', 'id'))";
    static final String INSERT = """
            INSERT INTO expense (id, paid_on, amount, category, vendor, description, asset, filename, media_type,
                content, sha256, size, seal, created_at, created_by)
            OVERRIDING SYSTEM VALUE
            VALUES (:id, :paidOn, :amount, :category, :vendor, :description, :asset, :filename, :mediaType, :content,
                :sha256, :size, :seal, :createdAt, :createdBy)""";
    static final String UPDATE = """
            UPDATE expense SET paid_on = :paidOn, amount = :amount, category = :category, vendor = :vendor,
                description = :description, asset = :asset
             WHERE id = :id AND cancelled_at IS NULL""";
    static final String CANCEL =
            "UPDATE expense SET cancelled_at = :at, cancel_reason = :reason WHERE id = :id AND cancelled_at IS NULL";

    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Seal seal;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param jdbc reads and writes the expenses
     * @param audit the audit log
     * @param seal seals the receipts
     * @param clock today, and the time an expense is entered
     */
    public ExpenseService(JdbcClient jdbc, AuditService audit, Seal seal, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.seal = seal;
        this.clock = clock;
    }

    /**
     * Returns today, the default day of a payment.
     *
     * @return the date in the zone of the clock
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Lists the expenses paid in a year, the cancelled ones included.
     *
     * @param year the year of the payment
     * @return the expenses, by day of payment
     */
    public List<Expense> ofYear(int year) {
        return jdbc.sql(OF_YEAR).param("from", LocalDate.of(year, 1, 1)).param("to", LocalDate.of(year, 12, 31))
                .query(ExpenseService::row).list();
    }

    /**
     * Returns the years with expenses.
     *
     * @return the years, ascending
     */
    public Set<Integer> years() {
        return new TreeSet<>(jdbc.sql(YEARS).query(Integer.class).list());
    }

    /**
     * Sums the valid expenses of a year by kind, the assets left out: WISO takes an asset with its AfA, so a sum that
     * held it would deduct it twice.
     *
     * @param expenses the expenses of the year
     * @return the sum of each kind that occurs, in the order of the kinds
     */
    public static Map<ExpenseCategory, BigDecimal> totals(List<Expense> expenses) {
        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        expenses.stream().filter(expense -> !expense.cancelled() && !expense.asset())
                .forEach(expense -> totals.merge(expense.category(), expense.amount(), BigDecimal::add));
        return totals;
    }

    /**
     * Returns the valid assets among the expenses of a year: they go into WISO one by one, as Anlagegut.
     *
     * @param expenses the expenses of the year
     * @return the assets that are not cancelled
     */
    public static List<Expense> assets(List<Expense> expenses) {
        return expenses.stream().filter(expense -> expense.asset() && !expense.cancelled()).toList();
    }

    /**
     * Returns one expense.
     *
     * @param id the ID
     * @return the expense, empty if there is none
     */
    public Optional<Expense> find(long id) {
        return jdbc.sql(ONE).param(ID, id).query(ExpenseService::row).optional();
    }

    /**
     * Enters an expense with its receipt.
     *
     * @param form the entered values
     * @param filename the name of the uploaded receipt, may be {@code null}
     * @param content the bytes of the receipt
     * @return the stored expense
     * @throws ExpenseException if a value or the receipt is not valid
     */
    @Transactional
    public Expense add(ExpenseForm form, String filename, byte[] content) {
        List<String> problems = new ArrayList<>();
        String mediaType = DocumentService.mediaType(problems, content).orElse(null);
        Details details = check(form, problems);
        long id = jdbc.sql(NEXT_ID).query(Long.class).single();
        String sha256 = Seal.sha256(content);
        details.bind(jdbc.sql(INSERT)).param(ID, id).param("filename", DocumentService.filename(filename, mediaType))
                .param("mediaType", mediaType).param("content", content).param("sha256", sha256)
                .param("size", content.length).param("seal", seal.expense(id, sha256))
                .param("createdAt", Timestamp.from(clock.instant())).param("createdBy", AuditService.currentUser())
                .update();
        Expense added = find(id).orElseThrow();
        audit.add(AuditAction.EXPENSE_ADDED, detail(added));
        return added;
    }

    /**
     * Changes the details of an expense. Its receipt stays as it is.
     *
     * @param id the expense
     * @param form the entered values
     * @return the changed expense
     * @throws ExpenseException if it is unknown or cancelled, or a value is not valid
     */
    @Transactional
    public Expense change(long id, ExpenseForm form) {
        Expense before = valid(id);
        Details details = check(form, new ArrayList<>());
        details.bind(jdbc.sql(UPDATE)).param(ID, id).update();
        Expense after = find(id).orElseThrow();
        audit.add(AuditAction.EXPENSE_CHANGED, detail(before) + " → " + detail(after));
        return after;
    }

    /**
     * Cancels an expense. It stays, with the reason, and counts no more.
     *
     * @param id the expense
     * @param reason why
     * @return the cancelled expense
     * @throws ExpenseException if it is unknown or cancelled already, or the reason is missing or too long
     */
    @Transactional
    public Expense cancel(long id, String reason) {
        valid(id);
        String text = Fields.text(reason);
        if (text == null) {
            throw new ExpenseException(List.of("Bitte den Grund angeben."));
        }
        if (text.length() > MAX_REASON) {
            throw new ExpenseException(List.of("Der Grund hat höchstens " + MAX_REASON + " Zeichen."));
        }
        jdbc.sql(CANCEL).param("at", Timestamp.from(clock.instant())).param("reason", text).param(ID, id).update();
        Expense cancelled = find(id).orElseThrow();
        audit.add(AuditAction.EXPENSE_CANCELLED, detail(cancelled) + ", Grund: " + text);
        return cancelled;
    }

    /**
     * Returns the receipt of an expense, after it passed the check.
     *
     * @param id the expense
     * @return the receipt, empty if there is none
     * @throws ExpenseException if the receipt does not match its SHA-256 or seal
     */
    public Optional<DocumentFile> file(long id) {
        return stored(id).map(file -> {
            if (!file.problems().isEmpty()) {
                throw new ExpenseException(List.of(DAMAGED.formatted(file.file().filename())));
            }
            return file.file();
        });
    }

    /**
     * Checks the receipt of an expense against its SHA-256 and seal.
     *
     * @param id the expense
     * @return what is wrong, empty if it is in order or there is no such expense
     */
    public List<String> problems(long id) {
        return stored(id).map(Stored::problems).orElse(List.of());
    }

    /**
     * Checks the receipt of every expense.
     *
     * @return the expenses with a problem
     */
    @Override
    public List<Damage> damaged() {
        List<Damage> damaged = new ArrayList<>();
        for (Long id : jdbc.sql(IDS).query(Long.class).list()) {
            Stored file = stored(id).orElseThrow();
            if (!file.problems().isEmpty()) {
                damaged.add(new Damage(null, "Beleg " + file.file().filename(), "/expenses/" + id, file.problems()));
            }
        }
        return damaged;
    }

    /**
     * Formats an amount as the pages and the audit log show it.
     *
     * @param amount the amount
     * @return for example {@code 1.234,50 €}
     */
    public static String euros(BigDecimal amount) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount) + " €";
    }

    private Expense valid(long id) {
        Expense expense = jdbc.sql(LOCKED).param(ID, id).query(ExpenseService::row).optional()
                .orElseThrow(() -> new ExpenseException(List.of(UNKNOWN)));
        if (expense.cancelled()) {
            throw new ExpenseException(List.of(CANCELLED));
        }
        return expense;
    }

    /** The checked values of a form. */
    private record Details(LocalDate paidOn, BigDecimal amount, ExpenseCategory category, String vendor,
            String description, boolean asset) {

        JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement) {
            return statement.param("paidOn", paidOn).param("amount", amount).param("category", category.name())
                    .param("vendor", vendor).param("description", description).param("asset", asset);
        }
    }

    /** Checks the values; with problems found before, it throws them all. */
    private Details check(ExpenseForm form, List<String> problems) {
        if (form.getPaidOn() == null) {
            problems.add("Bitte den Tag der Zahlung angeben.");
        } else if (form.getPaidOn().isAfter(today())) {
            problems.add("Der Tag der Zahlung liegt in der Zukunft.");
        }
        BigDecimal amount = amount(problems, form.getAmount());
        if (form.getCategory() == null) {
            problems.add("Bitte die Art wählen.");
        }
        String vendor = Fields.text(form.getVendor());
        String description = Fields.text(form.getDescription());
        Fields.required(problems, vendor, "Empfänger", MAX_VENDOR);
        Fields.required(problems, description, "Beschreibung", MAX_DESCRIPTION);
        if (!problems.isEmpty()) {
            throw new ExpenseException(problems);
        }
        return new Details(form.getPaidOn(), amount, form.getCategory(), vendor, description, form.isAsset());
    }

    private static BigDecimal amount(List<String> problems, String text) {
        if (text == null || text.isBlank()) {
            problems.add("Bitte den Betrag angeben.");
            return null;
        }
        if (DraftForm.ambiguous(text)) {
            problems.add("Der Betrag ist mehrdeutig: bitte mit Komma schreiben, zum Beispiel 1.500,00.");
            return null;
        }
        BigDecimal value = DraftForm.parse(text);
        if (value == null) {
            problems.add("Der Betrag ist keine Zahl, zum Beispiel 49,90.");
        } else if (value.signum() <= 0) {
            problems.add("Der Betrag ist größer als 0.");
        } else if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            problems.add("Der Betrag hat höchstens " + MAX_INTEGER_DIGITS + " Stellen vor dem Komma.");
        } else if (value.stripTrailingZeros().scale() > 2) {
            problems.add("Der Betrag hat höchstens 2 Nachkommastellen.");
        } else {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        }
        return null;
    }

    /** A receipt as it is stored, with what its check found. */
    private record Stored(DocumentFile file, List<String> problems) {
    }

    private Optional<Stored> stored(long id) {
        return jdbc.sql(FILE).param(ID, id).query((rs, n) -> {
            DocumentFile file = new DocumentFile(rs.getString(1), rs.getString(2), rs.getBytes(3));
            return new Stored(file, check(id, file, rs.getString(4), rs.getString(5)));
        }).optional();
    }

    private List<String> check(long id, DocumentFile file, String sha256, String stored) {
        List<String> problems = new ArrayList<>();
        String computed = Seal.sha256(file.content());
        if (!computed.equals(sha256)) {
            problems.add(WRONG_SHA.formatted(file.filename(), sha256, computed));
        }
        if (!Seal.matches(stored, seal.expense(id, sha256))) {
            problems.add(WRONG_SEAL.formatted(file.filename()));
        }
        return problems;
    }

    private static String detail(Expense expense) {
        return GERMAN.format(expense.paidOn()) + ", " + euros(expense.amount()) + ", " + expense.category().label()
                + ", " + expense.vendor() + ": " + expense.description() + (expense.asset() ? ", Anlagegut" : "");
    }

    private static Expense row(ResultSet rs, int n) throws SQLException {
        Timestamp cancelled = rs.getTimestamp("cancelled_at");
        return new Expense(rs.getLong(ID), rs.getObject("paid_on", LocalDate.class), rs.getBigDecimal("amount"),
                ExpenseCategory.valueOf(rs.getString("category")), rs.getString("vendor"), rs.getString("description"),
                rs.getBoolean("asset"), cancelled == null ? null : cancelled.toInstant(), rs.getString("cancel_reason"),
                rs.getString("filename"), rs.getString("media_type"), rs.getString("sha256"), rs.getInt("size"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by"));
    }
}
