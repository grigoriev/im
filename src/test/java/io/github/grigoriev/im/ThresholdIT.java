package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.threshold.Threshold;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.time.LocalDate;
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

    private static LineForm line(String text, String amount, TaxCategory category) {
        LineForm line = new LineForm();
        line.setText(text);
        line.setLumpSum(true);
        line.setAmount(amount);
        line.setCategory(category);
        return line;
    }

    /** An issued invoice dated on a day, with a §4 Nr. 21 line of 720 and a §19 line of 400: total 1 120. */
    private Invoice mixed(Customer customer, LocalDate day) {
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        form.setIssueDate(day);
        form.setServiceFrom(day);
        form.setServiceTo(day);
        form.setBuyerReference(customer.buyerReference());
        form.setTaxNote(null);
        form.getLines().clear();
        form.getLines().add(line("Unterricht", "720", TaxCategory.EXEMPT_4_21));
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
        sellers.save(SellerTestData.profile());
        numbers.savePattern("{YYYY}-{NN}");
        Customer customer = customers.create(CustomerTestData.verlag());
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

        int lastYearNumber = today.getYear() - 1;
        assertThatThrownBy(() -> threshold.saveBaseline(lastYearNumber, "18.000,00"))
                .as("last year has a payment in the app").hasMessage("Für dieses Jahr zählt die App die Zahlungen selbst.");
        assertThat(threshold.newestBaselineYear()).isLessThan(lastYearNumber);
        int older = threshold.newestBaselineYear();
        threshold.saveBaseline(older, "18.000,00");
        assertThat(threshold.baselines()).containsEntry(older, new java.math.BigDecimal("18000.00"));
        assertThat(threshold.current().previous()).as("an older year leaves last year as it is")
                .isEqualByComparingTo(after.previous());
        threshold.saveBaseline(older, "");
        assertThat(threshold.baselines()).doesNotContainKey(older);
        threshold.saveBaseline(lastYearNumber, "");
        assertThat(threshold.baselines()).as("removing works in a year with payments").doesNotContainKey(lastYearNumber);
    }
}
