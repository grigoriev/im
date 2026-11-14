package io.github.grigoriev.im.threshold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.seller.SettingsException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/**
 * The threshold with a fake database: payments per year with the snapshot of their invoice, and baselines. The sample
 * "reading" counts fully (§19, 480,00 €); "school" counts nothing (§4 Nr. 21); "mixed" counts 400 of 1 120.
 */
class ThresholdServiceTest {

    /** March 31 of 2027 in Berlin: day 90 of 365. */
    private static final Instant NOW = Instant.parse("2027-03-31T10:00:00Z");

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final DocumentStore documents = mock(DocumentStore.class);
    private final AuditService audit = mock(AuditService.class);
    private final ThresholdService service = new ThresholdService(jdbc, documents, audit,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    /** Payments by year: amount and snapshot key. */
    private final Map<Integer, List<Object[]>> payments = new HashMap<>();
    private final Map<Integer, BigDecimal> baselines = new HashMap<>();
    private final List<Map<String, Object>> writes = new ArrayList<>();

    private void paid(int year, String amount, String snapshot) {
        payments.computeIfAbsent(year, key -> new ArrayList<>()).add(new Object[] {new BigDecimal(amount), snapshot});
    }

    @SuppressWarnings("unchecked")
    private StatementSpec statement(String sql) {
        StatementSpec spec = mock(StatementSpec.class);
        Map<String, Object> params = new HashMap<>();
        params.put("sql", sql);
        when(spec.param(anyString(), any())).thenAnswer(call -> {
            params.put(call.getArgument(0), call.getArgument(1));
            return spec;
        });
        when(spec.update()).thenAnswer(call -> {
            writes.add(params);
            return 1;
        });
        MappedQuerySpec<Boolean> paid = mock(MappedQuerySpec.class);
        when(spec.query(Boolean.class)).thenReturn(paid);
        when(paid.single()).thenAnswer(call -> {
            assertThat(params).containsEntry("to", LocalDate.of(((LocalDate) params.get("from")).getYear(), 12, 31));
            return payments.containsKey(((LocalDate) params.get("from")).getYear());
        });
        MappedQuerySpec<Integer> years = mock(MappedQuerySpec.class);
        when(spec.query(Integer.class)).thenReturn(years);
        when(years.list()).thenAnswer(call -> payments.keySet().stream().sorted(java.util.Comparator.reverseOrder())
                .toList());
        MappedQuerySpec<BigDecimal> amount = mock(MappedQuerySpec.class);
        when(spec.query(BigDecimal.class)).thenReturn(amount);
        when(amount.optional()).thenAnswer(call -> Optional.ofNullable(baselines.get((Integer) params.get("year"))));
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<Object> mapper = call.getArgument(0);
            MappedQuerySpec<Object> rows = mock(MappedQuerySpec.class);
            when(rows.list()).thenAnswer(list -> rows(sql, params, mapper));
            return rows;
        });
        return spec;
    }

    private List<Object> rows(String sql, Map<String, Object> params, RowMapper<Object> mapper) throws Exception {
        List<Object[]> data = new ArrayList<>();
        if (sql.equals(ThresholdService.PAYMENTS)) {
            int year = ((LocalDate) params.get("from")).getYear();
            assertThat(params).containsEntry("to", LocalDate.of(year, 12, 31));
            data.addAll(payments.getOrDefault(year, List.of()));
        } else {
            baselines.entrySet().stream().sorted(Map.Entry.<Integer, BigDecimal>comparingByKey().reversed())
                    .forEach(entry -> data.add(new Object[] {entry.getKey(), entry.getValue()}));
        }
        List<Object> result = new ArrayList<>();
        for (Object[] row : data) {
            ResultSet rs = mock(ResultSet.class);
            if (row[0] instanceof BigDecimal value) {
                int year = ((LocalDate) params.get("from")).getYear();
                when(rs.getDate(1)).thenReturn(java.sql.Date.valueOf(LocalDate.of(year, 1, 15).plusDays(result.size())));
                when(rs.getBigDecimal(2)).thenReturn(value);
                when(rs.getString(3)).thenReturn(value.signum() < 0 ? "Erstattung" : null);
                when(rs.getString(4)).thenReturn(year + "-0" + (result.size() + 1));
                when(rs.getString(5)).thenReturn((String) row[1]);
            } else {
                when(rs.getInt(1)).thenReturn((Integer) row[0]);
                when(rs.getBigDecimal(2)).thenReturn((BigDecimal) row[1]);
            }
            result.add(mapper.mapRow(rs, result.size()));
        }
        return result;
    }

    @BeforeEach
    void database() {
        when(jdbc.sql(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
        when(documents.restore("reading")).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(documents.restore("school")).thenReturn(SampleInvoices.school(InvoiceFormat.ZUGFERD_EN16931));
        when(documents.restore("mixed")).thenReturn(SampleInvoices.mixed(InvoiceFormat.ZUGFERD_EN16931));
    }

    @Test
    void countsOnlyTheShareOfTheKleinunternehmerLinesByPaymentYear() {
        paid(2027, "480.00", "reading");
        paid(2027, "984.00", "school");
        paid(2027, "560.00", "mixed");
        paid(2026, "1000.00", "reading");
        Threshold threshold = service.current();
        assertThat(threshold.year()).isEqualTo(2027);
        assertThat(threshold.current()).as("480 + 0 + 560 x 400/1120").isEqualByComparingTo("680.00");
        assertThat(threshold.received()).isEqualByComparingTo("2024.00");
        assertThat(threshold.excluded()).isEqualByComparingTo("1344.00");
        assertThat(threshold.previous()).isEqualByComparingTo("1000.00");
        assertThat(threshold.previousFromBaseline()).isFalse();
        assertThat(threshold.forecast()).as("680 / 90 x 365").isEqualByComparingTo("2757.78");
    }

    @Test
    void aRefundSubtractsAndABaselineAdds() {
        paid(2027, "480.00", "reading");
        paid(2027, "-480.00", "reading");
        baselines.put(2026, new BigDecimal("18250.00"));
        Threshold threshold = service.current();
        assertThat(threshold.current()).isEqualByComparingTo("0.00");
        assertThat(threshold.received()).isEqualByComparingTo("0.00");
        assertThat(threshold.previous()).isEqualByComparingTo("18250.00");
        assertThat(threshold.previousFromBaseline()).isTrue();
        baselines.put(2027, new BigDecimal("5.00"));
        assertThat(service.current().current()).isEqualByComparingTo("5.00");
    }

    @Test
    void warnsWhenADraftWouldCrossTheLimit() {
        InvoiceDocument draft = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        baselines.put(2027, new BigDecimal("99520.00"));
        assertThat(service.wouldExceed(draft)).as("exactly 100 000").isFalse();
        baselines.put(2027, new BigDecimal("99520.01"));
        assertThat(service.wouldExceed(draft)).isTrue();
        assertThat(service.wouldExceed(SampleInvoices.school(InvoiceFormat.ZUGFERD_EN16931)))
                .as("§4 Nr. 21 does not count").isFalse();
        baselines.put(2027, new BigDecimal("99600.00"));
        assertThat(service.wouldExceed(SampleInvoices.twoThirds(InvoiceFormat.ZUGFERD_EN16931)))
                .as("400 of 600 reach exactly 100 000, no rounded share").isFalse();
        baselines.put(2027, new BigDecimal("99600.01"));
        assertThat(service.wouldExceed(SampleInvoices.twoThirds(InvoiceFormat.ZUGFERD_EN16931))).isTrue();
    }

    @Test
    void anInvoiceWithoutTotalCountsNothing() {
        RevenueRow row = ThresholdService.row(LocalDate.of(2027, 1, 2), new BigDecimal("5.00"), null, "2027-01",
                SampleInvoices.free(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(row.counted()).isEqualByComparingTo("0");
        assertThat(row.excluded()).isEqualByComparingTo("5.00");
        assertThat(row.byCategory()).isEmpty();
    }

    private static RevenueRow split(String amount, InvoiceDocument document) {
        return ThresholdService.row(LocalDate.of(2027, 1, 2), new BigDecimal(amount), null, "2027-01", document);
    }

    private static BigDecimal parts(RevenueRow row) {
        return row.byCategory().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void theCategoriesOfAPaymentAddUpToTheCent() {
        RevenueRow refund = split("-5.01", SampleInvoices.halves(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(refund.counted()).isEqualByComparingTo("-2.51");
        assertThat(refund.excluded()).isEqualByComparingTo("-2.50");
        assertThat(refund.byCategory()).containsEntry(TaxCategory.KLEINUNTERNEHMER, new BigDecimal("-2.51"))
                .containsEntry(TaxCategory.EXEMPT_4_21, new BigDecimal("-2.50"));
        assertThat(parts(refund)).isEqualByComparingTo("-5.01");
        RevenueRow tie = split("1.01", SampleInvoices.halves(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(parts(tie)).isEqualByComparingTo("1.01");
        assertThat(tie.byCategory()).containsEntry(TaxCategory.EXEMPT_4_21, tie.excluded());

        RevenueRow books = split("100.00", SampleInvoices.teachingAndBooks(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(books.byCategory()).doesNotContainKey(TaxCategory.KLEINUNTERNEHMER)
                .containsEntry(TaxCategory.EXEMPT_4_21, new BigDecimal("93.83"))
                .as("the VAT goes with the standard rate").containsEntry(TaxCategory.STANDARD_19, new BigDecimal("6.17"));
        assertThat(parts(books)).isEqualByComparingTo("100.00");

        RevenueRow sale = split("146.33", SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(sale.byCategory()).as("each rate with its own VAT")
                .containsEntry(TaxCategory.STANDARD_19, new BigDecimal("66.40"))
                .containsEntry(TaxCategory.STANDARD_7, new BigDecimal("79.93"));
        RevenueRow cents = split("0.02", SampleInvoices.fourWays(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(cents.byCategory().values()).as("no negative part of a positive payment")
                .allSatisfy(part -> assertThat(part.signum()).isNotNegative());
        assertThat(parts(cents)).isEqualByComparingTo("0.02");
        RevenueRow back = split("-0.02", SampleInvoices.fourWays(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(back.byCategory().values()).allSatisfy(part -> assertThat(part.signum()).isNotPositive());
        assertThat(parts(back)).isEqualByComparingTo("-0.02");
        RevenueRow four = split("105.96", SampleInvoices.fourWays(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(four.byCategory()).containsEntry(TaxCategory.EXEMPT_4_21, new BigDecimal("34.00"))
                .containsEntry(TaxCategory.REVERSE_CHARGE_EU, new BigDecimal("34.00"))
                .containsEntry(TaxCategory.STANDARD_19, new BigDecimal("36.89"))
                .containsEntry(TaxCategory.STANDARD_7, new BigDecimal("1.07"));

        RevenueRow reading = split("480.00", SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(reading.byCategory()).containsExactly(java.util.Map.entry(TaxCategory.KLEINUNTERNEHMER,
                new BigDecimal("480.00")));
        RevenueRow school = split("984.00", SampleInvoices.school(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(school.byCategory()).containsExactly(java.util.Map.entry(TaxCategory.EXEMPT_4_21,
                new BigDecimal("984.00")));
        RevenueRow tiny = split("0.01", SampleInvoices.mixed(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(tiny.counted()).isEqualByComparingTo("0.00");
        assertThat(tiny.byCategory()).containsEntry(TaxCategory.KLEINUNTERNEHMER, new BigDecimal("0.00"))
                .containsEntry(TaxCategory.EXEMPT_4_21, new BigDecimal("0.01"));
        assertThat(parts(tiny)).isEqualByComparingTo("0.01");
    }

    @Test
    void listsThePaymentsOfAYearAsRows() {
        paid(2027, "560.00", "mixed");
        paid(2027, "-100.00", "mixed");
        paid(2026, "1.00", "reading");
        List<RevenueRow> rows = service.rows(2027);
        assertThat(rows).hasSize(2);
        RevenueRow first = rows.getFirst();
        assertThat(first.paidOn()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(first.number()).isEqualTo("2027-01");
        assertThat(first.customer()).isEqualTo("Volkshochschule Musterstadt");
        assertThat(first.amount()).isEqualByComparingTo("560.00");
        assertThat(first.counted()).isEqualByComparingTo("200.00");
        assertThat(first.excluded()).isEqualByComparingTo("360.00");
        assertThat(first.byCategory()).containsEntry(TaxCategory.KLEINUNTERNEHMER,
                new BigDecimal("200.00")).containsEntry(TaxCategory.EXEMPT_4_21,
                new BigDecimal("360.00"));
        assertThat(first.note()).isNull();
        RevenueRow refund = rows.get(1);
        assertThat(refund.counted()).as("-100 x 400/1120, rounded half up").isEqualByComparingTo("-35.71");
        assertThat(refund.excluded()).isEqualByComparingTo("-64.29");
        assertThat(refund.note()).isEqualTo("Erstattung");
        assertThat(service.years()).containsExactly(2027, 2026);
        payments.clear();
        assertThat(service.years()).as("this year always").containsExactly(2027);
    }

    @Test
    void savesListsAndRemovesBaselines() {
        service.saveBaseline(2026, "18.250,5");
        assertThat(writes.getLast()).containsEntry("sql", ThresholdService.SAVE).containsEntry("year", 2026)
                .containsEntry("amount", new BigDecimal("18250.50"));
        verify(audit).add(AuditAction.BASELINE_SAVED, "2026, 18250.50");
        service.saveBaseline(2025, " ");
        assertThat(writes.getLast()).containsEntry("sql", ThresholdService.DELETE).containsEntry("year", 2025);
        verify(audit).add(AuditAction.BASELINE_SAVED, "2025, entfernt");
        service.saveBaseline(2000, null);
        baselines.put(2025, new BigDecimal("1.00"));
        baselines.put(2026, new BigDecimal("2.00"));
        assertThat(service.baselines()).containsExactly(Map.entry(2026, new BigDecimal("2.00")),
                Map.entry(2025, new BigDecimal("1.00")));
        assertThat(service.today()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    void refusesAYearOrAmountThatDoesNotFit() {
        for (int year : new int[] {1999, 2027, 2028}) {
            assertThatThrownBy(() -> service.saveBaseline(year, "1")).isInstanceOf(SettingsException.class)
                    .hasMessage("Ein Vorjahresumsatz gehört zu einem vergangenen Jahr.");
        }
        for (String amount : new String[] {"viel", "1.500", "-1", "1,001", "12345678901"}) {
            assertThatThrownBy(() -> service.saveBaseline(2026, amount)).isInstanceOf(SettingsException.class)
                    .hasMessage("Bitte den Umsatz als Betrag angeben, zum Beispiel 18.250,00.");
        }
        service.saveBaseline(2026, "1234567890,10");
        assertThat(writes.getLast()).containsEntry("amount", new BigDecimal("1234567890.10"));
        service.saveBaseline(2026, "0");
        assertThat(writes.getLast()).as("no revenue at all").containsEntry("amount", new BigDecimal("0.00"));
        service.saveBaseline(2026, "0,25");
        assertThat(writes.getLast()).containsEntry("amount", new BigDecimal("0.25"));
        verify(audit, never()).add(AuditAction.BASELINE_SAVED, "1999, entfernt");
    }

    @Test
    void aYearWithPaymentsInTheAppTakesNoBaseline() {
        assertThat(service.newestBaselineYear()).isEqualTo(2026);
        paid(2026, "100.00", "reading");
        paid(2025, "100.00", "reading");
        assertThatThrownBy(() -> service.saveBaseline(2026, "1.000,00"))
                .hasMessage("Für dieses Jahr zählt die App die Zahlungen selbst.");
        assertThat(service.newestBaselineYear()).isEqualTo(2024);
        service.saveBaseline(2026, "");
        assertThat(writes.getLast()).as("removing works in a year with payments").containsEntry("sql", ThresholdService.DELETE)
                .containsEntry("year", 2026);
        service.saveBaseline(2024, "1.000,00");
        assertThat(writes.getLast()).containsEntry("year", 2024);
        for (int year = 2000; year <= 2026; year++) {
            paid(year, "1.00", "reading");
        }
        assertThat(service.newestBaselineYear()).as("stops at 2000, the first year of a baseline").isEqualTo(2000);
    }

    @Test
    void knowsWhetherTheRuleApplies() {
        Threshold fine = new Threshold(2027, new BigDecimal("25000"), true, new BigDecimal("100000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("25000"));
        assertThat(fine.applies()).isTrue();
        assertThat(fine.nextYearAtRisk()).isFalse();
        assertThat(new Threshold(2027, new BigDecimal("25000.01"), true, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO).applies()).isFalse();
        Threshold over = new Threshold(2027, BigDecimal.ZERO, false, new BigDecimal("100000.01"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("25000.01"));
        assertThat(over.applies()).isFalse();
        assertThat(over.nextYearAtRisk()).isTrue();
        assertThat(Threshold.percent(new BigDecimal("12500"), Threshold.PREVIOUS_LIMIT)).isEqualTo(50);
        assertThat(Threshold.percent(new BigDecimal("24999.99"), Threshold.PREVIOUS_LIMIT)).isEqualTo(99);
        assertThat(Threshold.percent(new BigDecimal("30000"), Threshold.PREVIOUS_LIMIT)).isEqualTo(100);
        assertThat(Threshold.percent(new BigDecimal("-5"), Threshold.PREVIOUS_LIMIT)).isZero();
    }
}
