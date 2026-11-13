package io.github.grigoriev.im.numbering;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The counter of one year.
 *
 * @param year the year
 * @param nextNumber the counter of the next invoice
 * @param used whether the year has issued numbers; then the start value is locked
 */
@Table("number_sequence")
public record NumberSequence(@Id Integer year, int nextNumber, boolean used) {
}
