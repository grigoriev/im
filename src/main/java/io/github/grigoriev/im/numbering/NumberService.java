package io.github.grigoriev.im.numbering;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.seller.SettingsException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoice numbers: the pattern, the start value of a year and the assignment at issue. A number is assigned
 * in one statement under a row lock, so there are no gaps and no duplicates (docs/PLAN.md, section 7).
 */
@Service
public class NumberService {

    /** The key of the pattern in {@code app_setting}. */
    static final String PATTERN_KEY = "invoice-number-pattern";

    /** The highest start value. */
    static final int MAX_START = 999_999;

    static final String READ_PATTERN = "SELECT value FROM app_setting WHERE key = :key";
    static final String WRITE_PATTERN = """
            INSERT INTO app_setting (key, value) VALUES (:key, :value)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value""";
    static final String CREATE_YEAR = """
            INSERT INTO number_sequence (year, next_number, used) VALUES (:year, 1, FALSE)
            ON CONFLICT (year) DO NOTHING""";
    static final String TAKE = """
            UPDATE number_sequence SET next_number = next_number + 1, used = TRUE
             WHERE year = :year RETURNING next_number - 1""";
    static final String START = "UPDATE number_sequence SET next_number = :start WHERE year = :year AND NOT used";
    static final String TAKEN = "SELECT EXISTS (SELECT 1 FROM invoice WHERE number = :number)";

    private final NumberSequenceRepository sequences;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param sequences reads the counters
     * @param jdbc writes the pattern and the counters
     * @param clock source of the current year
     * @param audit the audit log
     */
    public NumberService(NumberSequenceRepository sequences, JdbcClient jdbc, Clock clock, AuditService audit) {
        this.sequences = sequences;
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
    }

    /**
     * Returns the pattern.
     *
     * @return the pattern of the admin, or {@link NumberPattern#DEFAULT}
     */
    public NumberPattern pattern() {
        return jdbc.sql(READ_PATTERN).param("key", PATTERN_KEY).query(String.class).optional()
                .map(NumberPattern::new).orElse(NumberPattern.DEFAULT);
    }

    /**
     * Checks and saves the pattern. It applies to the next number. A pattern whose next number of this year or the
     * next one already exists is refused: the issue would fail on the duplicate.
     *
     * @param text the pattern as entered
     * @return the saved pattern
     * @throws SettingsException if the pattern is invalid or its next number exists
     */
    @Transactional
    public NumberPattern savePattern(String text) {
        NumberPattern pattern = NumberPattern.parse(text);
        for (NumberSequence sequence : years()) {
            String next = pattern.format(sequence.year(), sequence.nextNumber());
            if (Boolean.TRUE.equals(jdbc.sql(TAKEN).param("number", next).query(Boolean.class).single())) {
                throw new SettingsException(List.of("Mit diesem Muster wäre die nächste Nummer " + next
                        + ", und die gibt es schon. Bitte das Muster ändern."));
            }
        }
        jdbc.sql(WRITE_PATTERN).param("key", PATTERN_KEY).param("value", pattern.value()).update();
        audit.add(AuditAction.NUMBER_PATTERN_SAVED, pattern.value());
        return pattern;
    }

    /**
     * Returns the current year and the next one, for the settings page.
     *
     * @return the counters, an unused year starts at 1
     */
    public List<NumberSequence> years() {
        int year = LocalDate.now(clock).getYear();
        return List.of(sequence(year), sequence(year + 1));
    }

    /**
     * Returns the number that the next invoice of a year gets.
     *
     * @param sequence the counter of the year
     * @return the number in the current pattern
     */
    public String preview(NumberSequence sequence) {
        return pattern().format(sequence.year(), sequence.nextNumber());
    }

    /**
     * Sets the counter of a year without issued numbers.
     *
     * @param year the year
     * @param start the counter of the next invoice, from 1
     * @throws SettingsException if the value is out of range or the year has issued numbers
     */
    @Transactional
    public void setStart(int year, int start) {
        if (start < 1 || start > MAX_START) {
            throw new SettingsException(List.of("Die Startnummer liegt zwischen 1 und " + MAX_START + "."));
        }
        jdbc.sql(CREATE_YEAR).param("year", year).update();
        if (jdbc.sql(START).param("start", start).param("year", year).update() == 0) {
            throw new SettingsException(List.of("Für " + year + " sind schon Nummern vergeben. Der Zähler ist gesperrt."));
        }
        audit.add(AuditAction.NUMBER_START_SET, year + ": " + start);
    }

    /**
     * Assigns the next number. It runs only in the transaction that issues the invoice: a rollback returns
     * the number, so no gap remains. Without such a transaction it fails, rather than commit a number alone.
     *
     * @param issueDate the issue date of the invoice
     * @return the invoice number
     * @throws org.springframework.transaction.IllegalTransactionStateException without a transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String assign(LocalDate issueDate) {
        int year = issueDate.getYear();
        jdbc.sql(CREATE_YEAR).param("year", year).update();
        int counter = Objects.requireNonNull(jdbc.sql(TAKE).param("year", year).query(Integer.class).single());
        return pattern().format(year, counter);
    }

    private NumberSequence sequence(int year) {
        return sequences.findById(year).orElse(new NumberSequence(year, 1, false));
    }
}
