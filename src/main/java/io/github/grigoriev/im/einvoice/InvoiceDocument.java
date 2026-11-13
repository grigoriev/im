package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An invoice as it is issued: the input for the PDF and the e-invoice XML.
 *
 * @param number invoice number
 * @param issueDate invoice date
 * @param serviceFrom first day of the service period
 * @param serviceTo last day of the service period
 * @param dueDate payment due date
 * @param seller seller
 * @param buyer buyer
 * @param buyerReference buyer reference (BT-10), the Leitweg-ID for a public buyer, may be {@code null}
 * @param iban IBAN of the seller
 * @param bic BIC of the seller
 * @param intro text above the lines
 * @param closing text below the lines
 * @param taxNote tax note, {@code null} for the default notes of the categories used
 * @param format e-invoice format
 * @param lines invoice lines, at least one
 */
public record InvoiceDocument(
        String number,
        LocalDate issueDate,
        LocalDate serviceFrom,
        LocalDate serviceTo,
        LocalDate dueDate,
        Party seller,
        Party buyer,
        String buyerReference,
        String iban,
        String bic,
        String intro,
        String closing,
        String taxNote,
        InvoiceFormat format,
        List<InvoiceLine> lines) {

    /** Checks the mandatory fields and the EN 16931 rules on the categories used. */
    public InvoiceDocument {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(serviceFrom, "serviceFrom");
        Objects.requireNonNull(serviceTo, "serviceTo");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(iban, "iban");
        Objects.requireNonNull(bic, "bic");
        Objects.requireNonNull(format, "format");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An invoice needs at least one line");
        }
        if (serviceTo.isBefore(serviceFrom)) {
            throw new IllegalArgumentException("The service period ends before it starts");
        }
        Set<TaxCategory> used = categoriesOf(lines);
        if (used.contains(TaxCategory.NOT_TAXABLE_ABROAD) && used.size() > 1) {
            throw new IllegalArgumentException("Category O never mixes with another category (BR-O-12)");
        }
        if (format.followsXRechnung() && (buyerReference == null || buyerReference.isBlank())) {
            throw new IllegalArgumentException("XRechnung needs a buyer reference (BR-DE-15)");
        }
    }

    private static Set<TaxCategory> categoriesOf(List<InvoiceLine> lines) {
        return lines.stream().map(InvoiceLine::category).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns the categories used, in the order of their first line.
     *
     * @return the categories
     */
    public Set<TaxCategory> categories() {
        return categoriesOf(lines);
    }

    /**
     * Returns the tax note to print: the given one, or the default notes of the categories used.
     *
     * @return the tax note, one line per category for the defaults
     */
    public String effectiveTaxNote() {
        if (taxNote != null && !taxNote.isBlank()) {
            return taxNote;
        }
        return categories().stream().map(TaxCategory::defaultNote).collect(Collectors.joining("\n"));
    }

    /**
     * Returns the invoice total. A Kleinunternehmerin charges no VAT, so net and gross are equal.
     *
     * @return the sum of the line amounts
     */
    public BigDecimal total() {
        return lines.stream().map(InvoiceLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
