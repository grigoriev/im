package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.CustomerReferences;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.LineUnit;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.Activity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class DraftFormTest {

    @Test
    void writesPricesAndQuantitiesGermanStyle() {
        assertThat(DraftForm.price(new BigDecimal("45"))).isEqualTo("45,00");
        assertThat(DraftForm.price(new BigDecimal("1234.5"))).isEqualTo("1234,50");
        assertThat(DraftForm.price(new BigDecimal("0.1234"))).isEqualTo("0,1234");
        assertThat(DraftForm.quantity(new BigDecimal("16.0000"))).isEqualTo("16");
        assertThat(DraftForm.quantity(new BigDecimal("1.5"))).isEqualTo("1,5");
    }

    @Test
    void fillsTheFormFromAnInvoice() {
        LocalDate day = LocalDate.of(2027, 3, 18);
        Invoice invoice = new Invoice(1L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.XRECHNUNG, day, day.minusDays(2),
                day.minusDays(1), 30, "04011000-12345-67", "Hallo", "Danke", "Steuerfrei.",
                List.of(new DraftLine("Unterricht", false, new BigDecimal("16"), new BigDecimal("45"), TaxCategory.EXEMPT_4_21),
                        new DraftLine("Lesung", true, BigDecimal.ONE, new BigDecimal("400"), TaxCategory.EXEMPT_4_21)),
                Instant.EPOCH, Instant.EPOCH, 0L, null, null, null,
                new CustomerReferences("70001234", "4500012345", "Rahmenvertrag"));
        DraftForm form = DraftForm.of(invoice);
        assertThat(form.getVersion()).isZero();
        assertThat(form.getSellerIdentifier()).isEqualTo("70001234");
        assertThat(form.getPurchaseOrder()).isEqualTo("4500012345");
        assertThat(form.getContract()).isEqualTo("Rahmenvertrag");
        assertThat(form.getFormat()).isEqualTo(InvoiceFormat.XRECHNUNG);
        assertThat(form.getIssueDate()).isEqualTo(day);
        assertThat(form.getServiceFrom()).isEqualTo(day.minusDays(2));
        assertThat(form.getServiceTo()).isEqualTo(day.minusDays(1));
        assertThat(form.getPaymentDays()).isEqualTo(30);
        assertThat(form.getBuyerReference()).isEqualTo("04011000-12345-67");
        assertThat(form.getIntro()).isEqualTo("Hallo");
        assertThat(form.getClosing()).isEqualTo("Danke");
        assertThat(form.getTaxNote()).isEqualTo("Steuerfrei.");
        assertThat(form.getLines()).hasSize(2);
        LineForm first = form.getLines().getFirst();
        assertThat(first.getText()).isEqualTo("Unterricht");
        assertThat(first.isLumpSum()).isFalse();
        assertThat(first.getQuantity()).isEqualTo("16");
        assertThat(first.getPrice()).isEqualTo("45,00");
        assertThat(first.getAmount()).as("the total, for a switch to a lump sum").isEqualTo("720,00");
        assertThat(first.getCategory()).isEqualTo(TaxCategory.EXEMPT_4_21);
        assertThat(form.getLines().get(1).isLumpSum()).isTrue();
        assertThat(form.getLines().get(1).getAmount()).isEqualTo("400,00");
        assertThat(first.isShownAsLumpSum()).isFalse();
        assertThat(first.getShownAmount()).isEqualTo("720,00");
        assertThat(form.getLines().get(1).isShownAsLumpSum()).isTrue();
    }

    @Test
    void keepsFourDecimalsOfALumpSum() {
        LocalDate day = LocalDate.of(2027, 3, 18);
        Invoice invoice = new Invoice(1L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_EN16931, day, day, day, 14,
                null, null, null, null, List.of(new DraftLine("Gebühr", true, BigDecimal.ONE, new BigDecimal("400.125"),
                        TaxCategory.KLEINUNTERNEHMER)), Instant.EPOCH, Instant.EPOCH, 0L, null, null, null);
        assertThat(DraftForm.of(invoice).getLines().getFirst().getAmount()).isEqualTo("400,125");
    }

    @Test
    void startsWithALumpSumLine() {
        LineForm line = new LineForm();
        assertThat(line.isLumpSum()).isTrue();
        assertThat(line.getQuantity()).isEqualTo("1");
        assertThat(line.getText()).isEmpty();
        assertThat(line.getPrice()).isEmpty();
        assertThat(line.getAmount()).isEmpty();
        assertThat(line.isShownAsLumpSum()).isTrue();
        assertThat(line.getShownAmount()).isEmpty();
        assertThat(new DraftForm().getLines()).isEmpty();
    }

    @Test
    void keepsTheBusinessOfTheInvoice() {
        Invoice invoice = new Invoice(5L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_EN16931,
                LocalDate.of(2027, 3, 18), LocalDate.of(2027, 3, 18), LocalDate.of(2027, 3, 18),
                14, null, null, null, null, List.of(), Instant.EPOCH, Instant.EPOCH, 0L, null,
                null, Activity.TEACHING);
        DraftForm form = DraftForm.of(invoice);
        Assertions.assertThat(form.getActivity()).isEqualTo(Activity.TEACHING);
        form.setActivity(Activity.OTHER);
        Assertions.assertThat(form.getActivity()).isEqualTo(Activity.OTHER);
    }

    @Test
    void fillsTheUnitAndThePeriodOfALine() {
        LocalDate day = LocalDate.of(2027, 3, 18);
        Invoice invoice = new Invoice(1L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_EN16931, day, day, day, 14,
                null, null, null, null, List.of(new DraftLine("Kurs", false, new BigDecimal("24"), new BigDecimal("40"),
                        TaxCategory.EXEMPT_4_21, LineUnit.TEACHING_UNIT, day.minusDays(9), day),
                        new DraftLine("Lesung", true, BigDecimal.ONE, BigDecimal.TEN, TaxCategory.EXEMPT_4_21)),
                Instant.EPOCH, Instant.EPOCH, 0L, null, null, null);
        LineForm first = DraftForm.of(invoice).getLines().getFirst();
        assertThat(first.getUnit()).isEqualTo(LineUnit.TEACHING_UNIT);
        assertThat(first.getServiceFrom()).isEqualTo(day.minusDays(9));
        assertThat(first.getServiceTo()).isEqualTo(day);
        LineForm second = DraftForm.of(invoice).getLines().get(1);
        assertThat(second.getUnit()).isEqualTo(LineUnit.PIECE);
        assertThat(second.getServiceFrom()).isNull();
        assertThat(second.getServiceTo()).isNull();
        assertThat(new LineForm().getUnit()).as("a new line counts pieces").isEqualTo(LineUnit.PIECE);
    }
}
