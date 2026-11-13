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
        InvoiceLine line = new InvoiceLine("x", new BigDecimal(quantity), "C62", "Stk", new BigDecimal(price), KU);
        assertThat(line.netAmount()).isEqualTo(new BigDecimal(amount));
    }

    @Test
    void acceptsFourDecimalsAndTrailingZeros() {
        InvoiceLine line = new InvoiceLine("x", new BigDecimal("1.2345"), "C62", "Stk", new BigDecimal("0.12340000"), KU);
        assertThat(line.quantity()).isEqualByComparingTo("1.2345");
    }

    @Test
    void rejectsMoreThanFourDecimals() {
        BigDecimal five = new BigDecimal("0.00001");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvoiceLine("x", five, "C62", "Stk", ONE, KU)).withMessageContaining("quantity");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvoiceLine("x", ONE, "C62", "Stk", five, KU)).withMessageContaining("unit price");
    }

    @Test
    void requiresEveryField() {
        assertThatNullPointerException()
                .isThrownBy(() -> new InvoiceLine(null, ONE, "C62", "Stk", ONE, KU)).withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> new InvoiceLine("x", null, "C62", "Stk", ONE, KU)).withMessage("quantity");
        assertThatNullPointerException()
                .isThrownBy(() -> new InvoiceLine("x", ONE, null, "Stk", ONE, KU)).withMessage("unitCode");
        assertThatNullPointerException()
                .isThrownBy(() -> new InvoiceLine("x", ONE, "C62", null, ONE, KU)).withMessage("unitLabel");
        assertThatNullPointerException()
                .isThrownBy(() -> new InvoiceLine("x", ONE, "C62", "Stk", null, KU)).withMessage("unitPrice");
        assertThatNullPointerException()
                .isThrownBy(() -> new InvoiceLine("x", ONE, "C62", "Stk", ONE, null)).withMessage("category");
    }
}
