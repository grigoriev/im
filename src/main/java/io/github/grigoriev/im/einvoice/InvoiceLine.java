package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One line of an invoice: a lump sum or a line with a quantity.
 *
 * <p>A lump sum shows the text and the amount only. The scope of the service belongs into the text,
 * for example {@code Lesung am 16.03.2027, 90 Min.} (§14 Abs. 4 Nr. 5 UStG). A line with a quantity
 * shows quantity with its unit, unit price and amount. The XML carries {@code LS} for a lump sum and the code of the
 * {@link LineUnit} for a counted line, as EN 16931 requires a unit on every line (BT-130). A line may name the day or
 * the period of its service (BG-26).
 *
 * @param text description of the service
 * @param quantity quantity, always 1 for a lump sum, at most four decimals
 * @param unitPrice net price per unit in EUR, at most four decimals
 * @param category VAT treatment
 * @param lumpSum whether the PDF shows the amount only
 * @param unit the unit of the quantity; {@code null}, as in a snapshot from before the units, means a piece
 * @param serviceFrom first day of the service of this line (BT-134), {@code null} without one
 * @param serviceTo last day of the service of this line (BT-135), {@code null} without one; the same day for one day
 */
public record InvoiceLine(String text, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category,
        boolean lumpSum, LineUnit unit, LocalDate serviceFrom, LocalDate serviceTo) {

    /** Decimals of quantity and unit price that the e-invoice XML carries. */
    static final int MAX_DECIMALS = 4;

    /** Checks the fields and the precision. */
    public InvoiceLine {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(category, "category");
        unit = unit == null ? LineUnit.PIECE : unit;
        if (quantity.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new IllegalArgumentException("A quantity has at most four decimals: " + quantity);
        }
        if (unitPrice.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new IllegalArgumentException("A unit price has at most four decimals: " + unitPrice);
        }
        if (lumpSum && quantity.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("A lump sum has quantity 1: " + quantity);
        }
        if ((serviceFrom == null) != (serviceTo == null)) {
            throw new IllegalArgumentException("A service period of a line has both days or none");
        }
        if (serviceFrom != null && serviceTo.isBefore(serviceFrom)) {
            throw new IllegalArgumentException("The service period of a line ends before it starts");
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
        return new InvoiceLine(text, BigDecimal.ONE, amount, category, true, LineUnit.PIECE, null, null);
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
        return counted(text, quantity, LineUnit.PIECE, unitPrice, category);
    }

    /**
     * Creates a line with a quantity in a unit.
     *
     * @param text description of the service
     * @param quantity quantity
     * @param unit unit of the quantity
     * @param unitPrice net price per unit in EUR
     * @param category VAT treatment
     * @return the line
     */
    public static InvoiceLine counted(String text, BigDecimal quantity, LineUnit unit, BigDecimal unitPrice,
            TaxCategory category) {
        return new InvoiceLine(text, quantity, unitPrice, category, false, unit, null, null);
    }

    /**
     * Returns this line with the day or period of its service.
     *
     * @param from first day, {@code null} for none
     * @param to last day, the first for one day, {@code null} for none
     * @return the line
     */
    public InvoiceLine withPeriod(LocalDate from, LocalDate to) {
        return new InvoiceLine(text, quantity, unitPrice, category, lumpSum, unit, from, to);
    }

    /**
     * Tells whether the line names the day or period of its service.
     *
     * @return {@code true} with a period
     */
    public boolean hasPeriod() {
        return serviceFrom != null;
    }

    /**
     * Returns the UN/ECE Recommendation 20 unit code for the XML.
     *
     * @return {@code LS} for a lump sum, the code of the unit otherwise
     */
    public String unitCode() {
        return lumpSum ? "LS" : unit.code();
    }

    /**
     * Returns the label the PDF prints after the quantity.
     *
     * @return for example "Std.", empty for a lump sum or a piece
     */
    public String unitLabel() {
        return lumpSum ? "" : unit.label(quantity);
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
