package io.github.grigoriev.im.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.seller.SettingsException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class ManualNumbersTest {

    private static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AuditService audit = mock(AuditService.class);
    private final ManualNumbers numbers = new ManualNumbers(jdbc, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    /** The counters in memory: year to manual, closed, used and next number. */
    private final Map<Integer, boolean[]> years = new TreeMap<>();
    private final Map<Integer, Integer> next = new HashMap<>();
    /** The issued invoices: ID, number, date. */
    private final List<Object[]> invoices = new ArrayList<>();
    private final Map<String, String> gaps = new TreeMap<>();

    @BeforeEach
    void database() {
        when(jdbc.sql(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
    }

    @SuppressWarnings("unchecked")
    private StatementSpec statement(String sql) {
        StatementSpec spec = mock(StatementSpec.class);
        Map<String, Object> params = new HashMap<>();
        when(spec.param(anyString(), any())).thenAnswer(call -> {
            params.put(call.getArgument(0), call.getArgument(1));
            return spec;
        });
        MappedQuerySpec<Boolean> flag = mock(MappedQuerySpec.class);
        when(spec.query(Boolean.class)).thenReturn(flag);
        when(flag.optional()).thenAnswer(call -> Optional.ofNullable(years.get((Integer) params.get("year")))
                .map(state -> state[0]));
        when(flag.single()).thenAnswer(call -> years.get((Integer) params.get("year"))[0]);
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<Object> mapper = call.getArgument(0);
            MappedQuerySpec<Object> query = mock(MappedQuerySpec.class);
            when(query.list()).thenAnswer(list -> rows(sql, params, mapper));
            when(query.optional()).thenAnswer(one -> rows(sql, params, mapper).stream().findFirst());
            return query;
        });
        when(spec.update()).thenAnswer(call -> update(sql, params));
        return spec;
    }

    private List<Object> rows(String sql, Map<String, Object> params, RowMapper<Object> mapper) throws SQLException {
        List<Object> rows = new ArrayList<>();
        int n = 0;
        if (sql.equals(ManualNumbers.STATE) || sql.equals(ManualNumbers.YEARS)) {
            for (Map.Entry<Integer, boolean[]> year : years.entrySet()) {
                boolean[] state = year.getValue();
                boolean wanted = sql.equals(ManualNumbers.STATE) ? year.getKey().equals(params.get("year"))
                        : state[0] || state[1];
                if (wanted) {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getInt("year")).thenReturn(year.getKey());
                    when(rs.getBoolean("manual")).thenReturn(state[0]);
                    when(rs.getBoolean("closed")).thenReturn(state[1]);
                    rows.add(0, mapper.mapRow(rs, n++));
                }
            }
        } else if (sql.equals(ManualNumbers.INVOICES)) {
            for (Object[] invoice : invoices) {
                LocalDate date = (LocalDate) invoice[2];
                if (!date.isBefore((LocalDate) params.get("from")) && !date.isAfter((LocalDate) params.get("to"))) {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong("id")).thenReturn((Long) invoice[0]);
                    when(rs.getString("number")).thenReturn((String) invoice[1]);
                    when(rs.getObject("issue_date", LocalDate.class)).thenReturn(date);
                    rows.add(mapper.mapRow(rs, n++));
                }
            }
        } else if (sql.equals(ManualNumbers.GAPS)) {
            for (Map.Entry<String, String> gap : gaps.entrySet()) {
                String[] key = gap.getKey().split("-");
                if (Integer.parseInt(key[0]) == (Integer) params.get("year")) {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getInt("counter")).thenReturn(Integer.parseInt(key[1]));
                    when(rs.getString("reason")).thenReturn(gap.getValue());
                    rows.add(mapper.mapRow(rs, n++));
                }
            }
        }
        return rows;
    }

    private int update(String sql, Map<String, Object> params) {
        int year = (Integer) params.get("year");
        boolean[] state = years.get(year);
        switch (sql) {
            case ManualNumbers.CREATE_YEAR -> years.putIfAbsent(year, new boolean[3]);
            case ManualNumbers.SET_MANUAL -> {
                if (state[1] || state[2]) {
                    return 0;
                }
                state[0] = (Boolean) params.get("manual");
            }
            case ManualNumbers.ADD_GAP -> gaps.put(year + "-" + params.get("counter"), (String) params.get("reason"));
            case ManualNumbers.CLOSE -> {
                state[0] = false;
                state[1] = true;
                state[2] = true;
                next.put(year, (Integer) params.get("next"));
            }
            default -> throw new IllegalStateException(sql);
        }
        return 1;
    }

    private void invoice(long id, String number, LocalDate date) {
        invoices.add(new Object[] {id, number, date});
    }

    @Test
    void switchesAYearToNumbersByHandAndBack() {
        assertThat(numbers.isManual(2026)).isFalse();
        numbers.setManual(2026, true);
        assertThat(numbers.isManual(2026)).isTrue();
        assertThat(numbers.years()).containsExactly(new ManualNumbers.YearState(2026, true, false));
        verify(audit).add(AuditAction.NUMBERS_BY_HAND, "2026: von Hand");
        numbers.setManual(2026, false);
        assertThat(numbers.years()).isEmpty();
        verify(audit).add(AuditAction.NUMBERS_BY_HAND, "2026: Zähler");

        assertThatThrownBy(() -> numbers.setManual(1999, true)).hasMessage(ManualNumbers.YEAR_RANGE);
        assertThatThrownBy(() -> numbers.setManual(10000, true)).hasMessage(ManualNumbers.YEAR_RANGE);
        years.put(2027, new boolean[] {false, false, true});
        assertThatThrownBy(() -> numbers.setManual(2027, true)).isInstanceOf(SettingsException.class)
                .hasMessage("Für 2027 sind schon Nummern vergeben oder das Jahr ist abgeschlossen.");

        numbers.setManual(2026, true);
        invoice(1, "2026-01", LocalDate.of(2026, 1, 5));
        assertThatThrownBy(() -> numbers.setManual(2026, false))
                .hasMessage("Für 2026 sind schon Rechnungen erfasst. Die Nummern von Hand bleiben.");
    }

    @Test
    void claimsAFreeNumberOfTheFile() {
        LocalDate march = LocalDate.of(2026, 3, 1);
        assertThat(numbers.claim(march, "2026-01")).as("no counter row: the counter").isEmpty();
        years.put(2026, new boolean[3]);
        assertThat(numbers.claim(march, "2026-01")).as("a year of the counter").isEmpty();
        years.get(2026)[0] = true;
        invoice(1, "2026-02", march);
        invoice(2, "RE-7", march);
        gaps.put("2026-3", "verschrieben");
        assertThat(numbers.claim(march, "2026-1")).contains("2026-1");
        assertThat(numbers.claim(march, "2026-004")).contains("2026-004");
        assertThatThrownBy(() -> numbers.claim(march, "2026-002")).hasMessage("Die Nummer 2026-002 gibt es schon: Rechnung 2026-02.");
        assertThatThrownBy(() -> numbers.claim(march, "2026-03")).hasMessage("Für die Nummer 2026-03 ist eine Lücke vermerkt.");
        assertThatThrownBy(() -> numbers.claim(march, "2025-05")).hasMessage("Die Nummer gehört zum Jahr 2025, das Rechnungsdatum zum Jahr 2026.");
        assertThatThrownBy(() -> numbers.claim(march, "R-2026-05")).hasMessage("Die Nummer hat die Form Jahr-Zähler, zum Beispiel 2026-07.");
        assertThatThrownBy(() -> numbers.claim(march, "2026-00")).as("a counter starts at 1")
                .hasMessage("Die Nummer hat die Form Jahr-Zähler, zum Beispiel 2026-07.");
    }

    @Test
    void listsInvoicesGapsAndDatesOutOfOrder() {
        years.put(2026, new boolean[] {true, false, false});
        assertThat(numbers.book(2026).entries()).isEmpty();
        invoice(1, "2026-01", LocalDate.of(2026, 1, 10));
        invoice(2, "2026-04", LocalDate.of(2026, 3, 1));
        invoice(3, "2026-02", LocalDate.of(2026, 1, 5));
        invoice(4, "2026-05", LocalDate.of(2026, 4, 1));
        invoice(5, "2025-09", LocalDate.of(2025, 12, 1));
        gaps.put("2026-3", "verschrieben");
        ManualNumbers.Book book = numbers.book(2026);
        assertThat(book.manual()).isTrue();
        assertThat(book.closed()).isFalse();
        assertThat(book.entries()).containsExactly(
                new ManualNumbers.Entry(1, "2026-01", 1L, LocalDate.of(2026, 1, 10), null, false),
                new ManualNumbers.Entry(2, "2026-02", 3L, LocalDate.of(2026, 1, 5), null, true),
                new ManualNumbers.Entry(3, null, null, null, "verschrieben", false),
                new ManualNumbers.Entry(4, "2026-04", 2L, LocalDate.of(2026, 3, 1), null, false),
                new ManualNumbers.Entry(5, "2026-05", 4L, LocalDate.of(2026, 4, 1), null, false));
        assertThat(book.openGaps()).isEmpty();
        assertThat(book.label(book.entries().get(2))).isEqualTo("2026-03");
        assertThat(book.label(book.entries().get(0))).isEqualTo("2026-01");
        assertThat(numbers.book(2030)).as("a year without a counter")
                .isEqualTo(new ManualNumbers.Book(2030, false, false, List.of()));
    }

    @Test
    void explainsAGapOnce() {
        years.put(2026, new boolean[] {true, false, false});
        invoice(1, "2026-01", LocalDate.of(2026, 1, 10));
        invoice(2, "2026-03", LocalDate.of(2026, 2, 10));
        numbers.explainGap(2026, 2, "  Nummer verschrieben ");
        assertThat(gaps).containsEntry("2026-2", "Nummer verschrieben");
        verify(audit).add(AuditAction.NUMBER_GAP_EXPLAINED, "2026-02: Nummer verschrieben");
        assertThatThrownBy(() -> numbers.explainGap(2026, 2, "nochmal")).hasMessage(ManualNumbers.GAP_KNOWN);
        assertThatThrownBy(() -> numbers.explainGap(2026, 1, "Rechnung")).hasMessage(ManualNumbers.GAP_WHERE);
        assertThatThrownBy(() -> numbers.explainGap(2026, 4, "darüber")).hasMessage(ManualNumbers.GAP_WHERE);
        assertThatThrownBy(() -> numbers.explainGap(2026, 2, " ")).hasMessage(ManualNumbers.REASON);
        assertThatThrownBy(() -> numbers.explainGap(2026, 2, null)).hasMessage(ManualNumbers.REASON);
        assertThatThrownBy(() -> numbers.explainGap(2026, 2, "x".repeat(501))).hasMessage(ManualNumbers.REASON);
        assertThatThrownBy(() -> numbers.explainGap(2027, 1, "Grund"))
                .hasMessage("Für 2027 werden die Nummern nicht von Hand erfasst.");
        years.put(2028, new boolean[3]);
        assertThatThrownBy(() -> numbers.explainGap(2028, 1, "Grund"))
                .hasMessage("Für 2028 werden die Nummern nicht von Hand erfasst.");
    }

    @Test
    void closesAYearWithoutAnOpenGap() {
        years.put(2026, new boolean[] {true, false, false});
        assertThatThrownBy(() -> numbers.close(2026)).hasMessage("Für 2026 ist noch keine Rechnung erfasst.");
        invoice(1, "2026-01", LocalDate.of(2026, 1, 10));
        invoice(2, "2026-04", LocalDate.of(2026, 2, 10));
        assertThatThrownBy(() -> numbers.close(2026)).hasMessage("Diese Nummern fehlen noch, ohne Grund: 2026-02, 2026-03.");
        gaps.put("2026-2", "verschrieben");
        gaps.put("2026-3", "storniert vor dem Versand");
        assertThat(numbers.close(2026)).isEqualTo(5);
        assertThat(next).containsEntry(2026, 5);
        assertThat(numbers.book(2026).closed()).isTrue();
        verify(audit).add(AuditAction.NUMBER_YEAR_CLOSED, "2026: 4 Nummern, weiter mit 5");
        assertThatThrownBy(() -> numbers.close(2026)).hasMessage("Für 2026 werden die Nummern nicht von Hand erfasst.");
        assertThatThrownBy(() -> numbers.close(2031)).hasMessage("Für 2031 werden die Nummern nicht von Hand erfasst.");
    }

    @Test
    void takesTheFirstAndTheLastYearAndAReasonOfTheLongestLength() {
        numbers.setManual(ManualNumbers.FIRST_YEAR, true);
        numbers.setManual(ManualNumbers.LAST_YEAR, true);
        assertThat(numbers.isManual(ManualNumbers.FIRST_YEAR)).isTrue();
        assertThat(numbers.isManual(ManualNumbers.LAST_YEAR)).isTrue();

        invoice(1, "2000-02", LocalDate.of(2000, 2, 1));
        String longest = "x".repeat(ManualNumbers.MAX_REASON);
        numbers.explainGap(2000, 1, longest);
        assertThat(gaps).containsEntry("2000-1", longest);
    }

    @Test
    void anInvoiceIsOutOfOrderAgainstTheLatestDateBeforeIt() {
        years.put(2026, new boolean[] {true, false, false});
        invoice(1, "2026-01", LocalDate.of(2026, 1, 10));
        invoice(2, "2026-02", LocalDate.of(2026, 3, 1));
        invoice(3, "2026-03", LocalDate.of(2026, 2, 1));
        assertThat(numbers.book(2026).entries()).extracting(ManualNumbers.Entry::outOfOrder)
                .as("2026-03 comes after the March invoice").containsExactly(false, false, true);
    }

    @Test
    void writesNothingForAWrongYear() {
        assertThatThrownBy(() -> numbers.setManual(1999, true)).isInstanceOf(SettingsException.class);
        verifyNoInteractions(audit);
    }
}
