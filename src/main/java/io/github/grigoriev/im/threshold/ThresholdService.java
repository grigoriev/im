package io.github.grigoriev.im.threshold;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceLine;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.seller.SettingsException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts the revenue of §19 UStG by payment received (docs/PLAN.md, section 2). A payment counts with the share of
 * the {@code KLEINUNTERNEHMER} lines in the total of its invoice; a refund subtracts the same way, in the year it goes
 * back. A year before the app adds its baseline.
 */
@Service
public class ThresholdService {

    static final String PAYMENTS = """
            SELECT p.paid_on, p.amount, p.note, i.number, i.snapshot FROM payment p JOIN invoice i ON i.id = p.invoice_id
             WHERE p.paid_on BETWEEN :from AND :to ORDER BY p.paid_on, p.id""";
    static final String YEARS = """
            SELECT CAST(EXTRACT(YEAR FROM paid_on) AS INTEGER) FROM payment
            UNION
            SELECT CAST(EXTRACT(YEAR FROM issue_date) AS INTEGER) FROM invoice WHERE status <> 'DRAFT'
            ORDER BY 1 DESC""";
    static final String BASELINES = "SELECT year, amount FROM threshold_baseline ORDER BY year DESC";
    static final String BASELINE = "SELECT amount FROM threshold_baseline WHERE year = :year";
    static final String SAVE = """
            INSERT INTO threshold_baseline (year, amount) VALUES (:year, :amount)
            ON CONFLICT (year) DO UPDATE SET amount = EXCLUDED.amount""";
    static final String DELETE = "DELETE FROM threshold_baseline WHERE year = :year";
    static final String PAID_IN = "SELECT EXISTS (SELECT 1 FROM payment WHERE paid_on BETWEEN :from AND :to)";
    static final String COUNTED_BY_APP = "Für dieses Jahr zählt die App die Zahlungen selbst.";

    private static final int CENTS = 2;

    /** The money of one year: what counts for §19 and what does not. */
    private record Revenue(BigDecimal counted, BigDecimal excluded) {
    }

    private final JdbcClient jdbc;
    private final DocumentStore documents;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param jdbc reads the payments and the baselines
     * @param documents reads the snapshot of an invoice
     * @param audit the audit log
     * @param clock source of today
     */
    public ThresholdService(JdbcClient jdbc, DocumentStore documents, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.documents = documents;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Returns the threshold of this year and the year before.
     *
     * @return the counted revenue, the forecast and what does not count
     */
    public Threshold current() {
        LocalDate today = LocalDate.now(clock);
        int year = today.getYear();
        Optional<BigDecimal> baseline = baseline(year - 1);
        BigDecimal previous = revenue(year - 1).counted().add(baseline.orElse(BigDecimal.ZERO));
        Revenue current = revenue(year);
        BigDecimal counted = current.counted().add(baseline(year).orElse(BigDecimal.ZERO));
        BigDecimal forecast = counted.multiply(BigDecimal.valueOf(today.lengthOfYear()))
                .divide(BigDecimal.valueOf(today.getDayOfYear()), CENTS, RoundingMode.HALF_UP);
        return new Threshold(year, previous, baseline.isPresent(), counted, current.excluded(),
                current.counted().add(current.excluded()), forecast);
    }

    /**
     * Tells whether a draft, once paid, would push this year's counted revenue above 100 000 euros.
     *
     * @param draft the document of the draft
     * @return {@code true} if its §19 lines and this year's counted revenue exceed the limit
     */
    public boolean wouldExceed(InvoiceDocument draft) {
        int year = LocalDate.now(clock).getYear();
        BigDecimal counted = revenue(year).counted().add(baseline(year).orElse(BigDecimal.ZERO));
        return counted.add(countedLines(draft)).compareTo(Threshold.CURRENT_LIMIT) > 0;
    }

    /**
     * Returns the baselines, the newest year first.
     *
     * @return the amount per year
     */
    public Map<Integer, BigDecimal> baselines() {
        Map<Integer, BigDecimal> result = new TreeMap<>(Comparator.reverseOrder());
        jdbc.sql(BASELINES).query((rs, row) -> Map.entry(rs.getInt(1), rs.getBigDecimal(2))).list()
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    /**
     * Saves the baseline of a year before this one and without payments in the app: a year the app counts itself must
     * not count twice. A blank amount removes it, also in a year that got payments later.
     *
     * @param year the year
     * @param amount the counted §19 revenue of that year, German style
     * @throws SettingsException if the year is not in the past, has payments, or the amount is no positive number
     */
    @Transactional
    public void saveBaseline(int year, String amount) {
        if (year < 2000 || year >= LocalDate.now(clock).getYear()) {
            throw new SettingsException(List.of("Ein Vorjahresumsatz gehört zu einem vergangenen Jahr."));
        }
        if (amount == null || amount.isBlank()) {
            jdbc.sql(DELETE).param("year", year).update();
            audit.add(AuditAction.BASELINE_SAVED, year + ", entfernt");
            return;
        }
        if (paidIn(year)) {
            // Removing stays possible: a payment entered later must never leave a double count behind.
            throw new SettingsException(List.of(COUNTED_BY_APP));
        }
        BigDecimal value = DraftForm.ambiguous(amount) ? null : DraftForm.parse(amount);
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > CENTS
                || value.precision() - value.scale() > 10) {
            throw new SettingsException(List.of("Bitte den Umsatz als Betrag angeben, zum Beispiel 18.250,00."));
        }
        jdbc.sql(SAVE).param("year", year).param("amount", value.setScale(CENTS, RoundingMode.UNNECESSARY)).update();
        audit.add(AuditAction.BASELINE_SAVED, year + ", " + value.setScale(CENTS, RoundingMode.UNNECESSARY));
    }

    /**
     * Returns today.
     *
     * @return today in the zone of the clock
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Returns the years of the reports: this year, every year with a payment and every year with an issued invoice,
     * the newest first.
     *
     * @return the years
     */
    public List<Integer> years() {
        SortedSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(LocalDate.now(clock).getYear());
        years.addAll(jdbc.sql(YEARS).query(Integer.class).list());
        return List.copyOf(years);
    }

    /**
     * Returns the newest past year without payments in the app, the default of a new baseline.
     *
     * @return last year, or the newest year before the first payment
     */
    public int newestBaselineYear() {
        int year = LocalDate.now(clock).getYear() - 1;
        while (year > 2000 && paidIn(year)) {
            year--;
        }
        return year;
    }

    private boolean paidIn(int year) {
        return Boolean.TRUE.equals(jdbc.sql(PAID_IN).param("from", LocalDate.of(year, 1, 1))
                .param("to", LocalDate.of(year, 12, 31)).query(Boolean.class).single());
    }

    private Optional<BigDecimal> baseline(int year) {
        return jdbc.sql(BASELINE).param("year", year).query(BigDecimal.class).optional();
    }

    private Revenue revenue(int year) {
        List<RevenueRow> rows = rows(year);
        return new Revenue(rows.stream().map(RevenueRow::counted).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(RevenueRow::excluded).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * Returns the payments of a year as the reports count them, in the order they came in.
     *
     * @param year the year
     * @return one row per payment, rounded to cents per payment
     */
    public List<RevenueRow> rows(int year) {
        return jdbc.sql(PAYMENTS).param("from", LocalDate.of(year, 1, 1)).param("to", LocalDate.of(year, 12, 31))
                .query((rs, row) -> row(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2), rs.getString(3),
                        rs.getString(4), documents.restore(rs.getString(5))))
                .list();
    }

    /**
     * Splits one payment by the lines of its invoice, to the cent. Each category weighs with its gross amount, its net
     * lines plus the VAT of its rate. The largest-remainder method hands out the cents: the parts add up to the
     * payment, and none takes the other sign. The §19 part is what {@code KLEINUNTERNEHMER} gets.
     */
    static RevenueRow row(LocalDate paidOn, BigDecimal amount, String note, String number, InvoiceDocument document) {
        Map<TaxCategory, BigDecimal> byCategory = new EnumMap<>(TaxCategory.class);
        if (document.total().signum() != 0) {
            byCategory.putAll(split(amount, gross(document), document.total()));
        }
        BigDecimal counted = byCategory.getOrDefault(TaxCategory.KLEINUNTERNEHMER, BigDecimal.ZERO.setScale(CENTS));
        return new RevenueRow(paidOn, number, document.buyer().name(), amount, counted, amount.subtract(counted),
                byCategory, note);
    }

    /** The gross amount per category: its net lines plus, for a standard rate, the VAT of that rate. */
    static Map<TaxCategory, BigDecimal> gross(InvoiceDocument document) {
        Map<TaxCategory, BigDecimal> gross = new EnumMap<>(TaxCategory.class);
        document.lines().forEach(line -> gross.merge(line.category(), line.netAmount(), BigDecimal::add));
        document.vatAmounts().forEach(vat -> gross.keySet().stream()
                .filter(category -> category.chargesVat() && category.rate().compareTo(vat.rate()) == 0)
                .findFirst().ifPresent(category -> gross.merge(category, vat.vat(), BigDecimal::add)));
        return gross;
    }

    /** Splits an amount by weights with the largest-remainder method, in cents. */
    static Map<TaxCategory, BigDecimal> split(BigDecimal amount, Map<TaxCategory, BigDecimal> weights,
            BigDecimal total) {
        BigDecimal cents = amount.abs().movePointRight(CENTS);
        Map<TaxCategory, BigDecimal> floors = new EnumMap<>(TaxCategory.class);
        Map<TaxCategory, BigDecimal> remainders = new EnumMap<>(TaxCategory.class);
        weights.forEach((category, weight) -> {
            BigDecimal exact = cents.multiply(weight).divide(total, 10, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(0, RoundingMode.FLOOR);
            floors.put(category, floor);
            remainders.put(category, exact.subtract(floor));
        });
        int left = cents.subtract(floors.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).intValueExact();
        // The biggest remainders get one cent each; a tie keeps the order of the enum.
        remainders.entrySet().stream()
                .sorted(Map.Entry.<TaxCategory, BigDecimal>comparingByValue().reversed())
                .limit(left).forEach(entry -> floors.merge(entry.getKey(), BigDecimal.ONE, BigDecimal::add));
        Map<TaxCategory, BigDecimal> parts = new EnumMap<>(TaxCategory.class);
        floors.forEach((category, floor) -> parts.put(category,
                floor.multiply(BigDecimal.valueOf(amount.signum())).movePointLeft(CENTS).setScale(CENTS)));
        return parts;
    }

    /** The amount of the lines that count for §19. */
    static BigDecimal countedLines(InvoiceDocument document) {
        return document.lines().stream().filter(line -> line.category().countsForThreshold())
                .map(InvoiceLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
