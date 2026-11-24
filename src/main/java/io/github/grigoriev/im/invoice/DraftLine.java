package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceLine;
import io.github.grigoriev.im.einvoice.LineUnit;
import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A line of an invoice as stored. A lump sum has quantity 1 and its amount as unit price. Quantity and unit
 * price have four decimals; more raise an {@link ArithmeticException}.
 *
 * @param text the service, for example "Lesung am 16.03.2027, 90 Min."
 * @param lumpSum whether the line is a lump sum
 * @param quantity the quantity, 1 for a lump sum
 * @param unitPrice the unit price, the amount for a lump sum
 * @param category the tax category
 * @param unit the unit of the quantity, a piece if {@code null}; a lump sum keeps it for a switch back
 * @param serviceFrom the first day of the service of the line, {@code null} without one
 * @param serviceTo the last day of the service of the line, {@code null} without one
 */
@Table("invoice_line")
public record DraftLine(String text, boolean lumpSum, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category,
        LineUnit unit, LocalDate serviceFrom, LocalDate serviceTo) {

    /** The scale of quantity and unit price, as in the database. */
    static final int SCALE = 4;

    /** Gives quantity and unit price the scale of the database, so a stored line equals the line it was. */
    @PersistenceCreator
    public DraftLine {
        quantity = scaled(quantity);
        unitPrice = scaled(unitPrice);
        unit = unit == null ? LineUnit.PIECE : unit;
    }

    /**
     * Creates a line in pieces without a day of service.
     *
     * @param text the service
     * @param lumpSum whether the line is a lump sum
     * @param quantity the quantity, 1 for a lump sum
     * @param unitPrice the unit price, the amount for a lump sum
     * @param category the tax category
     */
    public DraftLine(String text, boolean lumpSum, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category) {
        this(text, lumpSum, quantity, unitPrice, category, LineUnit.PIECE, null, null);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * Returns an empty lump-sum line.
     *
     * @param category the tax category
     * @return the line
     */
    public static DraftLine empty(TaxCategory category) {
        return new DraftLine("", true, BigDecimal.ONE, BigDecimal.ZERO, category);
    }

    /**
     * Returns the line of the invoice document. A lump sum leaves its unit behind: the XML carries {@code LS}.
     *
     * @return the line
     * @throws IllegalArgumentException if the line breaks a rule of {@link InvoiceLine}
     */
    public InvoiceLine toInvoiceLine() {
        InvoiceLine line = lumpSum ? InvoiceLine.lumpSum(text, unitPrice, category)
                : InvoiceLine.counted(text, quantity, unit, unitPrice, category);
        return line.withPeriod(serviceFrom, serviceTo);
    }
}
