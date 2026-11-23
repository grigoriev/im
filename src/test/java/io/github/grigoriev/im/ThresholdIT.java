package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.ExternalInvoiceService;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.invoice.SampleOffice;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.threshold.RevenueRow;
import io.github.grigoriev.im.threshold.Threshold;
import io.github.grigoriev.im.threshold.ThresholdService;
import io.github.grigoriev.im.web.ReportController;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The §19 threshold against PostgreSQL: payments by year, the share of the §19 lines in the stored snapshot, refunds
 * and the baseline. Other tests share the database, so this one compares before and after.
 */
@SpringBootTest
@Import(TestDatabase.class)
class ThresholdIT {

    @Autowired
    private ThresholdService threshold;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private IssueService issues;

    @Autowired
    private PaymentService payments;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private NumberService numbers;

    @Autowired
    private ExternalInvoiceService externals;

    @Autowired
    private ReportController reports;

    private static LineForm line(String text, String amount, TaxCategory category) {
        LineForm line = new LineForm();
        line.setText(text);
        line.setLumpSum(true);
        line.setAmount(amount);
        line.setCategory(category);
        return line;
    }

    /** An issued invoice dated on a day, with a reverse charge line of 720 and a §19 line of 400: total 1 120. */
    private Invoice mixed(Customer customer, LocalDate day) {
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        form.setIssueDate(day);
        form.setServiceFrom(day);
        form.setServiceTo(day);
        form.setBuyerReference(customer.buyerReference());
        form.setTaxNote(null);
        form.getLines().clear();
        form.getLines().add(line("Workshop für ein Unternehmen in der EU", "720", TaxCategory.REVERSE_CHARGE_EU));
        form.getLines().add(line("Lesung", "400", TaxCategory.KLEINUNTERNEHMER));
        Invoice saved = invoices.saveDraft(draft.id(), form);
        issues.issue(saved.id(), saved.version());
        return invoices.find(draft.id()).orElseThrow();
    }

    private static PaymentForm payment(LocalDate day, String amount) {
        PaymentForm form = new PaymentForm();
        form.setPaidOn(day);
        form.setAmount(amount);
        return form;
    }

    @Test
    void countsTheSharePerPaymentYearWithRefundsAndBaseline() {
        // A §19 line next to a reverse charge line: teaching never shares an invoice, reverse charge may.
        SellerProfile p = SellerTestData.profile();
        SellerTestData.save(sellers, new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), p.countryCode(),
                null, p.email(), p.phone(), p.taxNumber(), "DE123456789", p.iban(), p.bic(), p.taxStatus(), null, null,
                null, null, null, false));
        numbers.savePattern("{YYYY}-{NN}");
        Customer v = CustomerTestData.verlag();
        Customer customer = customers.create(new Customer(null, v.name(), null, v.street(), v.postcode(), v.city(), "AT", null,
                v.email(), "ATU12345678", null, v.format(), null));
        LocalDate today = threshold.today();
        LocalDate lastYear = LocalDate.of(today.getYear() - 1, 12, 30);
        Threshold before = threshold.current();

        Invoice old = mixed(customer, lastYear);
        payments.add(old.id(), payment(lastYear, "1.120,00"));
        Invoice fresh = mixed(customer, LocalDate.of(today.getYear(), 1, 1));
        payments.add(fresh.id(), payment(today, "560,00"));
        payments.add(old.id(), payment(today, "-112,00"));

        Threshold after = threshold.current();
        assertThat(after.previous().subtract(before.previous())).as("400 of the 1 120 paid last year")
                .isEqualByComparingTo("400.00");
        assertThat(after.current().subtract(before.current())).as("560 x 400/1120 - 112 x 400/1120")
                .isEqualByComparingTo("160.00");
        assertThat(after.excluded().subtract(before.excluded())).isEqualByComparingTo("288.00");
        assertThat(after.received().subtract(before.received())).isEqualByComparingTo("448.00");

        List<RevenueRow> rows = threshold.rows(today.getYear()).stream()
                .filter(row -> row.number().equals(fresh.number()) || row.number().equals(old.number())).toList();
        assertThat(rows).extracting(RevenueRow::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("560.00"), new BigDecimal("-112.00"));
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.paidOn()).isEqualTo(today);
            assertThat(row.customer()).isEqualTo("Verlag am Fluss GmbH");
            assertThat(row.counted().add(row.excluded())).isEqualByComparingTo(row.amount());
        });
        assertThat(threshold.years()).contains(today.getYear(), today.getYear() - 1);

        int lastYearNumber = today.getYear() - 1;
        assertThatThrownBy(() -> threshold.saveBaseline(lastYearNumber, "18.000,00"))
                .as("last year has a payment in the app").hasMessage("Für dieses Jahr zählt die App die Zahlungen selbst.");
        assertThat(threshold.newestBaselineYear()).isLessThan(lastYearNumber);
        int older = threshold.newestBaselineYear();
        threshold.saveBaseline(older, "18.000,00");
        assertThat(threshold.baselines()).containsEntry(older, new BigDecimal("18000.00"));
        assertThat(threshold.current().previous()).as("an older year leaves last year as it is")
                .isEqualByComparingTo(after.previous());
        threshold.saveBaseline(older, "");
        assertThat(threshold.baselines()).doesNotContainKey(older);
        threshold.saveBaseline(lastYearNumber, "");
        assertThat(threshold.baselines()).as("removing works in a year with payments").doesNotContainKey(lastYearNumber);
    }

    @Test
    void aPaymentCountsInTheYearItArrivesInNotInTheYearOfTheInvoice() {
        SellerTestData.save(sellers, SellerTestData.profile());
        numbers.savePattern("{YYYY}-{NN}");
        Customer customer = customers.create(CustomerTestData.verlag());
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        LocalDate newYearsEve = LocalDate.of(2008, 12, 31);
        form.setIssueDate(newYearsEve);
        form.setServiceFrom(newYearsEve);
        form.setServiceTo(newYearsEve);
        form.setBuyerReference(customer.buyerReference());
        form.getLines().clear();
        form.getLines().add(line("Lesung", "400", TaxCategory.KLEINUNTERNEHMER));
        Invoice saved = invoices.saveDraft(draft.id(), form);
        issues.issue(saved.id(), saved.version());
        Invoice invoice = invoices.find(saved.id()).orElseThrow();

        payments.add(invoice.id(), payment(newYearsEve, "100,00"));
        payments.add(invoice.id(), payment(LocalDate.of(2009, 1, 1), "300,00"));
        payments.add(invoice.id(), payment(LocalDate.of(2010, 1, 4), "-50,00"));

        assertThat(counted(2008, invoice)).as("the last day of the year").containsExactly(new BigDecimal("100.00"));
        assertThat(counted(2009, invoice)).as("the first day of the next one")
                .containsExactly(new BigDecimal("300.00"));
        assertThat(counted(2010, invoice)).as("a refund counts against the year it goes out")
                .containsExactly(new BigDecimal("-50.00"));
        assertThat(threshold.years()).contains(2008, 2009, 2010);
        assertThatThrownBy(() -> threshold.saveBaseline(2009, "1.000,00"))
                .as("a year with nothing but payments of an invoice of the year before")
                .hasMessage("Für dieses Jahr zählt die App die Zahlungen selbst.");
    }

    @Test
    void aPaidExternalInvoiceCountsForTheLimitAndInTheCsv() {
        SellerTestData.save(sellers, SellerTestData.profile());
        numbers.savePattern("{YYYY}-{NN}");
        Customer v = CustomerTestData.verlag();
        Customer customer = customers.create(new Customer(null, v.name(), null, v.street(), v.postcode(), v.city(),
                v.countryCode(), null, v.email(), null, null, InvoiceFormat.EXTERNAL, null));
        LocalDate day = LocalDate.of(2022, 3, 18);
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        form.setIssueDate(day);
        form.setServiceFrom(day);
        form.setServiceTo(day);
        form.getLines().clear();
        form.getLines().add(line("Lesung", "400", TaxCategory.KLEINUNTERNEHMER));
        form.getLines().add(line("Kurs", "720", TaxCategory.EXEMPT_4_21));
        Invoice saved = invoices.saveDraft(draft.id(), form);
        String number = externals.register(saved.id(), saved.version(), "2022-01", "Rechnung.xlsx",
                SampleOffice.workbook());
        payments.add(saved.id(), payment(LocalDate.of(2022, 4, 1), "1.120,00"));

        assertThat(threshold.rows(2022)).filteredOn(row -> row.number().equals(number)).singleElement()
                .satisfies(row -> {
                    assertThat(row.counted()).as("the §19 line").isEqualByComparingTo("400.00");
                    assertThat(row.excluded()).as("the teaching line").isEqualByComparingTo("720.00");
                    assertThat(row.byCategory()).containsEntry(TaxCategory.EXEMPT_4_21, new BigDecimal("720.00"));
                });
        String csv = new String(reports.csv(2022).getBody(), StandardCharsets.UTF_8);
        assertThat(csv).contains("01.04.2022;\"2022-01\";\"Verlag am Fluss GmbH\";1120,00;400,00;720,00;720,00;");
    }

    /** The §19 part of each payment of the invoice in a year; all of an invoice with §19 lines only. */
    private List<BigDecimal> counted(int year, Invoice invoice) {
        List<RevenueRow> rows = threshold.rows(year).stream().filter(row -> row.number().equals(invoice.number()))
                .toList();
        assertThat(rows).allSatisfy(row -> assertThat(row.counted()).isEqualByComparingTo(row.amount()));
        return rows.stream().map(RevenueRow::counted).toList();
    }
}
