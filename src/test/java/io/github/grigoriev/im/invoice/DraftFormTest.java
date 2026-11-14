package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DraftFormTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "45          | 45",
        "45,5        | 45.5",
        "45.50       | 45.50",
        "1.234,56    | 1234.56",
        "1.234.567,8 | 1234567.8",
        "' 400 € '   | 400",
        "-10,00      | -10.00",
        "0,0001      | 0.0001"
    })
    void readsAmountsAsGermansTypeThem(String text, BigDecimal amount) {
        assertThat(DraftForm.parse(text)).isEqualByComparingTo(amount);
    }

    @ParameterizedTest
    @CsvSource({"1.500, true", "12.000, true", "-1.500, true", "' 1.500 € ', true", "1.5, false", "45.50, false",
        "'1.500,00', false", "1500, false", "1234.567, false"})
    void namesTheAmbiguousDotBeforeThreeDigits(String text, boolean ambiguous) {
        assertThat(DraftForm.ambiguous(text)).isEqualTo(ambiguous);
        if (ambiguous) {
            assertThat(DraftForm.parse(text)).isNull();
        }
        assertThat(DraftForm.ambiguous(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"''", "abc", "1,2,3", "12.34.56", "1.23,4", "4 5x"})
    void refusesWhatIsNoNumber(String text) {
        assertThat(DraftForm.parse(text)).isNull();
        assertThat(DraftForm.parse(null)).isNull();
    }

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
                Instant.EPOCH, Instant.EPOCH, 0L);
        DraftForm form = DraftForm.of(invoice);
        assertThat(form.getVersion()).isZero();
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
        assertThat(first.getCategory()).isEqualTo(TaxCategory.EXEMPT_4_21);
        assertThat(form.getLines().get(1).isLumpSum()).isTrue();
    }

    @Test
    void startsWithALumpSumLine() {
        LineForm line = new LineForm();
        assertThat(line.isLumpSum()).isTrue();
        assertThat(line.getQuantity()).isEqualTo("1");
        assertThat(line.getText()).isEmpty();
        assertThat(line.getPrice()).isEmpty();
        assertThat(new DraftForm().getLines()).isEmpty();
    }
}
