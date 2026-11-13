package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * One line of an invoice.
 *
 * @param text description of the service
 * @param quantity quantity
 * @param unitCode UN/ECE Recommendation 20 unit code, for example {@code C62} or {@code HUR}
 * @param unitLabel unit as printed on the PDF, for example {@code Stk}
 * @param unitPrice net price per unit in EUR
 * @param category VAT treatment
 */
public record InvoiceLine(
        String text,
        BigDecimal quantity,
        String unitCode,
        String unitLabel,
        BigDecimal unitPrice,
        TaxCategory category) {

    /** Checks that every field is present. */
    public InvoiceLine {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitCode, "unitCode");
        Objects.requireNonNull(unitLabel, "unitLabel");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(category, "category");
    }

    /**
     * Returns the net amount of the line, rounded to cents.
     *
     * @return quantity times unit price
     */
    public BigDecimal netAmount() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
