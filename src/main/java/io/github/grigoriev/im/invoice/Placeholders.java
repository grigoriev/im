package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import java.time.LocalDate;

/**
 * The values of the placeholders in the texts of an invoice (docs/PLAN.md, section 7), formatted as the PDF shows
 * them: {@code {Rechnungsnummer}}, {@code {Rechnungsdatum}}, {@code {Leistungszeitraum}} and {@code {Zahlungsziel}}.
 *
 * @param number the invoice number, "Entwurf" before the issue
 * @param issueDate the invoice date
 * @param serviceFrom first day of the service
 * @param serviceTo last day of the service
 * @param dueDate the day the payment is due
 */
public record Placeholders(String number, LocalDate issueDate, LocalDate serviceFrom, LocalDate serviceTo,
        LocalDate dueDate) {

    /**
     * Returns the placeholders of an invoice.
     *
     * @param invoice the invoice
     * @param number its number, or "Entwurf"
     * @return the placeholders
     */
    public static Placeholders of(Invoice invoice, String number) {
        return new Placeholders(number, invoice.issueDate(), invoice.serviceFrom(), invoice.serviceTo(),
                invoice.dueDate());
    }

    /**
     * Replaces the placeholders in a text. Unknown placeholders stay as typed.
     *
     * @param text the text, may be {@code null}
     * @return the text with the values, {@code null} for {@code null}
     */
    public String fill(String text) {
        if (text == null) {
            return null;
        }
        InvoicePdfRenderer.Formatter format = new InvoicePdfRenderer.Formatter();
        return text.replace("{Rechnungsnummer}", number)
                .replace("{Rechnungsdatum}", format.date(issueDate))
                .replace("{Leistungszeitraum}", format.period(serviceFrom, serviceTo))
                .replace("{Zahlungsziel}", format.date(dueDate));
    }
}
