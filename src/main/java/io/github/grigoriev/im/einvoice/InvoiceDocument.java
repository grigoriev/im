package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
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
 * @param cancels the invoice this Stornorechnung cancels, {@code null} for an invoice; a Stornorechnung is a credit note
 *     (type 381) with the lines of the cancelled invoice
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
        List<InvoiceLine> lines,
        InvoiceReference cancels) {

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
        if (used.contains(TaxCategory.KLEINUNTERNEHMER) && used.stream().anyMatch(TaxCategory::chargesVat)) {
            throw new IllegalArgumentException("A Kleinunternehmer charges no VAT (§19 UStG)");
        }
        if (used.contains(TaxCategory.REVERSE_CHARGE_EU) && (seller.vatId() == null || buyer.vatId() == null)) {
            throw new IllegalArgumentException("Reverse charge needs the VAT ID of seller and buyer (§14a UStG)");
        }
        if (format.followsXRechnung() && (buyerReference == null || buyerReference.isBlank())) {
            throw new IllegalArgumentException("XRechnung needs a buyer reference (BR-DE-15)");
        }
    }

    /**
     * Creates an invoice that cancels nothing.
     *
     * @param number invoice number
     * @param issueDate invoice date
     * @param serviceFrom first day of the service period
     * @param serviceTo last day of the service period
     * @param dueDate payment due date
     * @param seller seller
     * @param buyer buyer
     * @param buyerReference buyer reference (BT-10), may be {@code null}
     * @param iban IBAN of the seller
     * @param bic BIC of the seller
     * @param intro text above the lines
     * @param closing text below the lines
     * @param taxNote tax note, {@code null} for the default notes of the categories used
     * @param format e-invoice format
     * @param lines invoice lines, at least one
     */
    @SuppressWarnings("java:S107") // the fields of an invoice
    public InvoiceDocument(String number, LocalDate issueDate, LocalDate serviceFrom, LocalDate serviceTo,
            LocalDate dueDate, Party seller, Party buyer, String buyerReference, String iban, String bic, String intro,
            String closing, String taxNote, InvoiceFormat format, List<InvoiceLine> lines) {
        this(number, issueDate, serviceFrom, serviceTo, dueDate, seller, buyer, buyerReference, iban, bic, intro, closing,
                taxNote, format, lines, null);
    }

    /**
     * Tells whether this is a Stornorechnung.
     *
     * @return {@code true} if it cancels an invoice
     */
    public boolean isCancellation() {
        return cancels != null;
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
        return categories().stream().map(TaxCategory::defaultNote).filter(note -> !note.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Tells whether the PDF shows the quantity and unit price columns. They appear only when at least
     * one line is not a lump sum.
     *
     * @return {@code true} if a line has its own quantity
     */
    public boolean showsQuantities() {
        return lines.stream().anyMatch(line -> !line.lumpSum());
    }

    /**
     * Tells whether the PDF shows the tax column. With more than one category, each line names its
     * rate or exemption (§14 Abs. 4 Nr. 8 UStG).
     *
     * @return {@code true} if the lines use more than one category
     */
    public boolean showsTaxColumn() {
        return categories().size() > 1;
    }

    /**
     * Returns the number of columns of the lines table: text and amount, plus quantity and unit price,
     * plus the tax column.
     *
     * @return the column count
     */
    public int columns() {
        return 2 + (showsQuantities() ? 2 : 0) + (showsTaxColumn() ? 1 : 0);
    }

    /**
     * Returns the sum of the net line amounts.
     *
     * @return the net total
     */
    public BigDecimal netTotal() {
        return lines.stream().map(InvoiceLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Returns the VAT per rate, highest rate first. Lines without VAT do not appear.
     *
     * <p>EN 16931 computes the VAT of a rate on the sum of its line amounts and rounds once (BR-CO-17).
     *
     * @return the VAT amounts, empty when no line charges VAT
     */
    public List<VatAmount> vatAmounts() {
        Map<BigDecimal, BigDecimal> bases = new TreeMap<>(Comparator.reverseOrder());
        lines.stream().filter(line -> line.category().chargesVat())
                .forEach(line -> bases.merge(line.category().rate(), line.netAmount(), BigDecimal::add));
        return bases.entrySet().stream()
                .map(entry -> new VatAmount(entry.getKey(), entry.getValue(),
                        entry.getValue().multiply(entry.getKey()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    /**
     * Returns the VAT total.
     *
     * @return the sum of the VAT amounts, zero without VAT
     */
    public BigDecimal vatTotal() {
        return vatAmounts().stream().map(VatAmount::vat).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Returns the amount due: net total plus VAT.
     *
     * @return the gross total
     */
    public BigDecimal total() {
        return netTotal().add(vatTotal());
    }
}
