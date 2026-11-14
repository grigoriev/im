package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A line of a template, the same as a {@link DraftLine} in its own table.
 *
 * @param text the service
 * @param lumpSum whether the line is a lump sum
 * @param quantity the quantity, 1 for a lump sum
 * @param unitPrice the unit price, the amount for a lump sum
 * @param category the tax category
 */
@Table("invoice_template_line")
public record TemplateLine(String text, boolean lumpSum, BigDecimal quantity, BigDecimal unitPrice, TaxCategory category) {

    /** Gives quantity and unit price the scale of the database, as {@link DraftLine} does. */
    public TemplateLine {
        DraftLine line = new DraftLine(text, lumpSum, quantity, unitPrice, category);
        quantity = line.quantity();
        unitPrice = line.unitPrice();
    }

    /**
     * Returns the template line of a line.
     *
     * @param line the line
     * @return the template line
     */
    public static TemplateLine of(DraftLine line) {
        return new TemplateLine(line.text(), line.lumpSum(), line.quantity(), line.unitPrice(), line.category());
    }

    /**
     * Returns the line for a draft.
     *
     * @return the line
     */
    public DraftLine toDraftLine() {
        return new DraftLine(text, lumpSum, quantity, unitPrice, category);
    }
}
