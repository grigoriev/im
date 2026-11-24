package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.LineUnit;
import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A line of a template, the same as a {@link DraftLine} in its own table.
 *
 * @param text the service
 * @param lumpSum whether the line is a lump sum
 * @param quantity the quantity, 1 for a lump sum
 * @param unitPrice the unit price, the amount for a lump sum
 * @param category the tax category
 * @param unit the unit of the quantity, a piece if {@code null}
 * @param serviceFrom the first day of the service of the line, {@code null} without one
 * @param serviceTo the last day of the service of the line, {@code null} without one
 */
@Table("invoice_template_line")
public record TemplateLine(String text, boolean lumpSum, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category,
        LineUnit unit, LocalDate serviceFrom, LocalDate serviceTo) {

    /** Gives quantity and unit price the scale of the database, as {@link DraftLine} does. */
    @PersistenceCreator
    public TemplateLine {
        DraftLine line = new DraftLine(text, lumpSum, quantity, unitPrice, category, unit, serviceFrom, serviceTo);
        quantity = line.quantity();
        unitPrice = line.unitPrice();
        unit = line.unit();
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
    public TemplateLine(String text, boolean lumpSum, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category) {
        this(text, lumpSum, quantity, unitPrice, category, LineUnit.PIECE, null, null);
    }

    /**
     * Returns the template line of a line.
     *
     * @param line the line
     * @return the template line
     */
    public static TemplateLine of(DraftLine line) {
        return new TemplateLine(line.text(), line.lumpSum(), line.quantity(), line.unitPrice(), line.category(),
                line.unit(), line.serviceFrom(), line.serviceTo());
    }

    /**
     * Returns the line for a draft.
     *
     * @return the line
     */
    public DraftLine toDraftLine() {
        return new DraftLine(text, lumpSum, quantity, unitPrice, category, unit, serviceFrom, serviceTo);
    }
}
