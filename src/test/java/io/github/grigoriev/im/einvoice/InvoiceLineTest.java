package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceLineTest {

    private static final TaxCategory KU = TaxCategory.KLEINUNTERNEHMER;
    private static final BigDecimal ONE = BigDecimal.ONE;

    @ParameterizedTest
    @CsvSource({
        "1, 400.00, 400.00",
        "120, 4.50, 540.00",
        "1.5, 33.33, 50.00",
        "3, 0.005, 0.02",
        "0.333, 1, 0.33"
    })
    void roundsTheNetAmountHalfUpToCents(String quantity, String price, String amount) {
        InvoiceLine line = InvoiceLine.counted("x", new BigDecimal(quantity), new BigDecimal(price), KU);
        assertThat(line.netAmount()).isEqualTo(new BigDecimal(amount));
    }

    @Test
    void createsALumpSumWithUnitLs() {
        InvoiceLine line = InvoiceLine.lumpSum("Lesung am 16.03.2027, 90 Min.", new BigDecimal("400.00"), KU);
        assertThat(line.lumpSum()).isTrue();
        assertThat(line.quantity()).isEqualTo(ONE);
        assertThat(line.unitPrice()).isEqualByComparingTo("400.00");
        assertThat(line.netAmount()).isEqualByComparingTo("400.00");
        assertThat(line.unitCode()).isEqualTo("LS");
        assertThat(line.category()).isEqualTo(KU);
        assertThat(line.text()).isEqualTo("Lesung am 16.03.2027, 90 Min.");
    }

    @Test
    void createsACountedLineWithUnitOne() {
        InvoiceLine line = InvoiceLine.counted("Lektorat, je Normseite", new BigDecimal("120"), new BigDecimal("4.50"), KU);
        assertThat(line.lumpSum()).isFalse();
        assertThat(line.unitCode()).isEqualTo("C62");
        assertThat(line.netAmount()).isEqualByComparingTo("540.00");
    }

    @Test
    void rejectsALumpSumWithAnotherQuantity() {
        BigDecimal two = new BigDecimal("2");
        assertThatIllegalArgumentException().isThrownBy(() -> new InvoiceLine("x", two, ONE, KU, true))
                .withMessageContaining("quantity 1");
        assertThat(new InvoiceLine("x", new BigDecimal("1.000"), ONE, KU, true).lumpSum()).isTrue();
    }

    @Test
    void acceptsFourDecimalsAndTrailingZeros() {
        InvoiceLine line = InvoiceLine.counted("x", new BigDecimal("1.2345"), new BigDecimal("0.12340000"), KU);
        assertThat(line.quantity()).isEqualByComparingTo("1.2345");
    }

    @Test
    void rejectsMoreThanFourDecimals() {
        BigDecimal five = new BigDecimal("0.00001");
        assertThatIllegalArgumentException().isThrownBy(() -> InvoiceLine.counted("x", five, ONE, KU))
                .withMessageContaining("quantity");
        assertThatIllegalArgumentException().isThrownBy(() -> InvoiceLine.counted("x", ONE, five, KU))
                .withMessageContaining("unit price");
    }

    @Test
    void requiresEveryField() {
        assertThatNullPointerException().isThrownBy(() -> InvoiceLine.counted(null, ONE, ONE, KU)).withMessage("text");
        assertThatNullPointerException().isThrownBy(() -> InvoiceLine.counted("x", null, ONE, KU)).withMessage("quantity");
        assertThatNullPointerException().isThrownBy(() -> InvoiceLine.counted("x", ONE, null, KU)).withMessage("unitPrice");
        assertThatNullPointerException().isThrownBy(() -> InvoiceLine.counted("x", ONE, ONE, null)).withMessage("category");
    }
}
