package io.github.grigoriev.im.numbering;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.seller.SettingsException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers of a year before the app (docs/PLAN.md, section 7, "External invoices"). The invoices of such a year come
 * in as external invoices with the number of their file, in any order. Each gap needs a reason. "Jahr abschließen" ends
 * the year: the counter continues after the highest number.
 */
@Service
public class ManualNumbers {

    /** A number by hand: the year, a hyphen and the counter, for example {@code 2026-07}. */
    static final Pattern NUMBER = Pattern.compile("(\\d{4})-(\\d{1,6})");
    static final int MAX_REASON = 500;
    /** The first year a counter may have. */
    public static final int FIRST_YEAR = 2000;
    /** The last year a counter may have. */
    public static final int LAST_YEAR = 9999;
    static final String YEAR_RANGE = "Das Jahr liegt zwischen " + FIRST_YEAR + " und " + LAST_YEAR + ".";

    static final String WRONG_FORM = "Die Nummer hat die Form Jahr-Zähler, zum Beispiel %d-07.";
    static final String WRONG_YEAR = "Die Nummer gehört zum Jahr %s, das Rechnungsdatum zum Jahr %d.";
    static final String TAKEN = "Die Nummer %s gibt es schon: Rechnung %s.";
    static final String GAP = "Für die Nummer %d-%02d ist eine Lücke vermerkt.";
    static final String NOT_FREE = "Für %d sind schon Nummern vergeben oder das Jahr ist abgeschlossen.";
    static final String HAS_INVOICES = "Für %d sind schon Rechnungen erfasst. Die Nummern von Hand bleiben.";
    static final String NOT_MANUAL = "Für %d werden die Nummern nicht von Hand erfasst.";
    static final String GAP_WHERE = "Eine Lücke liegt zwischen 1 und der höchsten Nummer des Jahres, ohne Rechnung.";
    static final String GAP_KNOWN = "Diese Lücke ist schon begründet.";
    static final String REASON = "Bitte einen Grund mit höchstens " + MAX_REASON + " Zeichen angeben.";
    static final String EMPTY = "Für %d ist noch keine Rechnung erfasst.";
    static final String OPEN_GAPS = "Diese Nummern fehlen noch, ohne Grund: %s.";

    static final String CREATE_YEAR = """
            INSERT INTO number_sequence (year, next_number, used) VALUES (:year, 1, FALSE)
            ON CONFLICT (year) DO NOTHING""";
    static final String STATE = "SELECT year, manual, closed FROM number_sequence WHERE year = :year";
    static final String LOCK = "SELECT manual FROM number_sequence WHERE year = :year FOR UPDATE";
    static final String YEARS = "SELECT year, manual, closed FROM number_sequence WHERE manual OR closed "
            + "ORDER BY year DESC";
    static final String SET_MANUAL = "UPDATE number_sequence SET manual = :manual WHERE year = :year AND NOT used "
            + "AND NOT closed";
    static final String INVOICES = """
            SELECT id, number, issue_date FROM invoice
             WHERE status <> 'DRAFT' AND issue_date BETWEEN :from AND :to ORDER BY id""";
    static final String GAPS = "SELECT counter, reason FROM number_gap WHERE year = :year";
    static final String ADD_GAP = """
            INSERT INTO number_gap (year, counter, reason, created_at, created_by)
            VALUES (:year, :counter, :reason, :at, :by)""";
    static final String CLOSE = """
            UPDATE number_sequence SET next_number = :next, used = TRUE, manual = FALSE, closed = TRUE
             WHERE year = :year AND manual""";

    private static final String YEAR = "year";

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    /**
     * A year by hand or closed, for the settings.
     *
     * @param year the year
     * @param manual whether its invoices come in by hand now
     * @param closed whether "Jahr abschließen" ended it
     */
    public record YearState(int year, boolean manual, boolean closed) {
    }

    /**
     * One number of a year by hand: an invoice, or a gap with or without its reason.
     *
     * @param counter the counter
     * @param number the number as printed, {@code null} for a gap
     * @param invoiceId the invoice, {@code null} for a gap
     * @param date the invoice date, {@code null} for a gap
     * @param reason the reason of a gap, {@code null} while it has none and for an invoice
     * @param outOfOrder whether an invoice with a lower number has a later date
     */
    public record Entry(int counter, String number, Long invoiceId, LocalDate date, String reason,
            boolean outOfOrder) {

        /**
         * Tells whether the number has no invoice.
         *
         * @return {@code true} for a gap
         */
        public boolean gap() {
            return invoiceId == null;
        }
    }

    /**
     * The numbers of a year by hand, from 1 to the highest one.
     *
     * @param year the year
     * @param manual whether its invoices come in by hand now
     * @param closed whether "Jahr abschließen" ended it
     * @param entries one entry per counter, in order
     */
    public record Book(int year, boolean manual, boolean closed, List<Entry> entries) {

        /** Keeps a copy of the entries. */
        public Book {
            entries = List.copyOf(entries);
        }

        /**
         * Lists the gaps without a reason.
         *
         * @return the gaps that block the closing
         */
        public List<Entry> openGaps() {
            return entries.stream().filter(entry -> entry.gap() && entry.reason() == null).toList();
        }

        /**
         * Returns the number of an entry: as printed for an invoice, year and counter for a gap.
         *
         * @param entry an entry of this year
         * @return for example {@code 2026-03}
         */
        public String label(Entry entry) {
            return entry.gap() ? "%d-%02d".formatted(year, entry.counter()) : entry.number();
        }
    }

    /** An invoice of the year with its counter. */
    private record Numbered(int counter, String number, long id, LocalDate date) {
    }

    /**
     * Creates the service.
     *
     * @param jdbc reads and writes the counters, gaps and numbers
     * @param audit the audit log
     * @param clock the time a reason is written
     */
    public ManualNumbers(JdbcClient jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Tells whether the invoices of a year come in by hand.
     *
     * @param year the year
     * @return {@code true} for a year before the app, until it is closed
     */
    public boolean isManual(int year) {
        return state(year).manual();
    }

    private YearState state(int year) {
        return jdbc.sql(STATE).param(YEAR, year).query(ManualNumbers::yearState).optional()
                .orElse(new YearState(year, false, false));
    }

    private static YearState yearState(ResultSet rs, int n) throws SQLException {
        return new YearState(rs.getInt(YEAR), rs.getBoolean("manual"), rs.getBoolean("closed"));
    }

    /**
     * Lists the years by hand and the closed ones.
     *
     * @return the years, the newest first
     */
    public List<YearState> years() {
        return jdbc.sql(YEARS).query(ManualNumbers::yearState).list();
    }

    /**
     * Turns the numbers by hand of a year on or off. On needs a year without numbers of the counter; off needs a year
     * without invoices.
     *
     * @param year the year
     * @param manual whether its invoices come in by hand
     * @throws SettingsException if the year has numbers of the counter, is closed, or has invoices by hand
     */
    @Transactional
    public void setManual(int year, boolean manual) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw new SettingsException(List.of(YEAR_RANGE));
        }
        jdbc.sql(CREATE_YEAR).param(YEAR, year).update();
        jdbc.sql(LOCK).param(YEAR, year).query(Boolean.class).single();
        if (!manual && !numbered(year).isEmpty()) {
            throw new SettingsException(List.of(HAS_INVOICES.formatted(year)));
        }
        if (jdbc.sql(SET_MANUAL).param("manual", manual).param(YEAR, year).update() == 0) {
            throw new SettingsException(List.of(NOT_FREE.formatted(year)));
        }
        audit.add(AuditAction.NUMBERS_BY_HAND, year + (manual ? ": von Hand" : ": Zähler"));
    }

    /**
     * Claims the number of a file for an invoice of a year by hand. It locks the counter of the year until the
     * transaction ends, so two registrations never take one number.
     *
     * @param issueDate the invoice date
     * @param entered the number of the file
     * @return the number as entered, empty if the year takes its numbers from the counter
     * @throws SettingsException if the number has another form or year, exists, or names a gap
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> claim(LocalDate issueDate, String entered) {
        int year = issueDate.getYear();
        Optional<Boolean> manual = jdbc.sql(LOCK).param(YEAR, year).query(Boolean.class).optional();
        if (!manual.orElse(false)) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER.matcher(entered);
        if (!matcher.matches()) {
            throw new SettingsException(List.of(WRONG_FORM.formatted(year)));
        }
        if (Integer.parseInt(matcher.group(1)) != year) {
            throw new SettingsException(List.of(WRONG_YEAR.formatted(matcher.group(1), year)));
        }
        int counter = Integer.parseInt(matcher.group(2));
        if (counter < 1) {
            throw new SettingsException(List.of(WRONG_FORM.formatted(year)));
        }
        for (Numbered taken : numbered(year)) {
            if (taken.counter() == counter) {
                throw new SettingsException(List.of(TAKEN.formatted(entered, taken.number())));
            }
        }
        if (gaps(year).containsKey(counter)) {
            throw new SettingsException(List.of(GAP.formatted(year, counter)));
        }
        return Optional.of(entered);
    }

    /**
     * Returns the numbers of a year by hand: each invoice, each gap with or without its reason.
     *
     * @param year the year
     * @return the book of the year
     */
    public Book book(int year) {
        TreeMap<Integer, Numbered> invoices = new TreeMap<>();
        numbered(year).forEach(found -> invoices.put(found.counter(), found));
        Map<Integer, String> gaps = gaps(year);
        int highest = invoices.isEmpty() ? 0 : invoices.lastKey();
        List<Entry> entries = new ArrayList<>();
        LocalDate latest = null;
        for (int counter = 1; counter <= highest; counter++) {
            Numbered invoice = invoices.get(counter);
            if (invoice == null) {
                entries.add(new Entry(counter, null, null, null, gaps.get(counter), false));
            } else {
                boolean outOfOrder = latest != null && invoice.date().isBefore(latest);
                latest = latest == null || invoice.date().isAfter(latest) ? invoice.date() : latest;
                entries.add(new Entry(counter, invoice.number(), invoice.id(), invoice.date(), null, outOfOrder));
            }
        }
        YearState state = state(year);
        return new Book(year, state.manual(), state.closed(), entries);
    }

    /**
     * Writes the reason of a gap. It never changes.
     *
     * @param year the year
     * @param counter the missing counter
     * @param reason why the number has no invoice
     * @throws SettingsException if the year is not by hand, the number is no gap, has a reason, or the reason is
     *     empty or too long
     */
    @Transactional
    public void explainGap(int year, int counter, String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty() || text.length() > MAX_REASON) {
            throw new SettingsException(List.of(REASON));
        }
        Optional<Boolean> manual = jdbc.sql(LOCK).param(YEAR, year).query(Boolean.class).optional();
        if (!manual.orElse(false)) {
            throw new SettingsException(List.of(NOT_MANUAL.formatted(year)));
        }
        Optional<Entry> entry = book(year).entries().stream().filter(found -> found.counter() == counter).findFirst();
        if (entry.isEmpty() || !entry.get().gap()) {
            throw new SettingsException(List.of(GAP_WHERE));
        }
        if (entry.get().reason() != null) {
            throw new SettingsException(List.of(GAP_KNOWN));
        }
        jdbc.sql(ADD_GAP).param(YEAR, year).param("counter", counter).param("reason", text)
                .param("at", Timestamp.from(clock.instant())).param("by", AuditService.currentUser()).update();
        audit.add(AuditAction.NUMBER_GAP_EXPLAINED, "%d-%02d: %s".formatted(year, counter, text));
    }

    /**
     * Closes a year by hand: the counter continues after its highest number, and the year takes no number by hand.
     *
     * @param year the year
     * @return the number the next invoice of the year would get from the counter
     * @throws SettingsException if the year is not by hand, has no invoice or a gap without a reason
     */
    @Transactional
    public int close(int year) {
        Optional<Boolean> manual = jdbc.sql(LOCK).param(YEAR, year).query(Boolean.class).optional();
        if (!manual.orElse(false)) {
            throw new SettingsException(List.of(NOT_MANUAL.formatted(year)));
        }
        Book book = book(year);
        if (book.entries().isEmpty()) {
            throw new SettingsException(List.of(EMPTY.formatted(year)));
        }
        List<Entry> open = book.openGaps();
        if (!open.isEmpty()) {
            throw new SettingsException(List.of(OPEN_GAPS.formatted(String.join(", ",
                    open.stream().map(book::label).toList()))));
        }
        int next = book.entries().size() + 1;
        jdbc.sql(CLOSE).param("next", next).param(YEAR, year).update();
        audit.add(AuditAction.NUMBER_YEAR_CLOSED, year + ": " + book.entries().size() + " Nummern, weiter mit " + next);
        return next;
    }

    /** The invoices of a year whose number has the form of a number by hand. */
    private List<Numbered> numbered(int year) {
        return jdbc.sql(INVOICES).param("from", LocalDate.of(year, 1, 1)).param("to", LocalDate.of(year, 12, 31))
                .query((rs, n) -> {
                    Matcher matcher = NUMBER.matcher(rs.getString("number"));
                    return matcher.matches() ? new Numbered(Integer.parseInt(matcher.group(2)), rs.getString("number"),
                            rs.getLong("id"), rs.getObject("issue_date", LocalDate.class)) : null;
                }).list().stream().filter(Objects::nonNull).toList();
    }

    private Map<Integer, String> gaps(int year) {
        Map<Integer, String> gaps = new TreeMap<>();
        jdbc.sql(GAPS).param(YEAR, year).query((rs, n) -> Map.entry(rs.getInt("counter"), rs.getString("reason")))
                .list().forEach(gap -> gaps.put(gap.getKey(), gap.getValue()));
        return gaps;
    }
}
