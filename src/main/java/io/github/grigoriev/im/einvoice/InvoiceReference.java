package io.github.grigoriev.im.einvoice;

import java.time.LocalDate;
import java.util.Objects;

/**
 * An earlier invoice this one refers to, for example the invoice a Stornorechnung cancels (BT-25, BT-26).
 *
 * @param number its number
 * @param issueDate its invoice date
 */
public record InvoiceReference(String number, LocalDate issueDate) {

    /** Checks both fields. */
    public InvoiceReference {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(issueDate, "issueDate");
    }
}
