package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * One line of an invoice: a lump sum or a line with a quantity.
 *
 * <p>A lump sum shows the text and the amount only. The scope of the service belongs into the text,
 * for example {@code Lesung am 16.03.2027, 90 Min.} (§14 Abs. 4 Nr. 5 UStG). A line with a quantity
 * shows quantity, unit price and amount. The user never picks a unit: the XML carries {@code LS} for a
 * lump sum and {@code C62} (one) for a counted line, as EN 16931 requires a unit on every line.
 *
 * @param text description of the service
 * @param quantity quantity, always 1 for a lump sum, at most four decimals
 * @param unitPrice net price per unit in EUR, at most four decimals
 * @param category VAT treatment
 * @param lumpSum whether the PDF shows the amount only
 */
public record InvoiceLine(String text, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category,
        boolean lumpSum) {

    /** Decimals of quantity and unit price that the e-invoice XML carries. */
    static final int MAX_DECIMALS = 4;

    /** Checks the fields and the precision. */
    public InvoiceLine {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(category, "category");
        if (quantity.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new IllegalArgumentException("A quantity has at most four decimals: " + quantity);
        }
        if (unitPrice.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new IllegalArgumentException("A unit price has at most four decimals: " + unitPrice);
        }
        if (lumpSum && quantity.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("A lump sum has quantity 1: " + quantity);
        }
    }

    /**
     * Creates a lump-sum line.
     *
     * @param text description of the service with its scope
     * @param amount net amount in EUR
     * @param category VAT treatment
     * @return the line
     */
    public static InvoiceLine lumpSum(String text, BigDecimal amount, TaxCategory category) {
        return new InvoiceLine(text, BigDecimal.ONE, amount, category, true);
    }

    /**
     * Creates a line with a quantity.
     *
     * @param text description of the service
     * @param quantity quantity
     * @param unitPrice net price per unit in EUR
     * @param category VAT treatment
     * @return the line
     */
    public static InvoiceLine counted(String text, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category) {
        return new InvoiceLine(text, quantity, unitPrice, category, false);
    }

    /**
     * Returns the UN/ECE Recommendation 20 unit code for the XML.
     *
     * @return {@code LS} for a lump sum, {@code C62} otherwise
     */
    public String unitCode() {
        return lumpSum ? "LS" : "C62";
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
