package io.github.grigoriev.im.seller;

import io.github.grigoriev.im.einvoice.TaxCategory;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A default tax note that the admin changed. Categories without a row use {@link TaxCategory#defaultNote()}.
 *
 * @param category the name of the tax category
 * @param text the note
 */
@Table("tax_note")
record TaxNote(@Id String category, String text) {
}
