package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.springframework.stereotype.Component;

/** Maps an {@link InvoiceDocument} to the invoice model of the Mustang library. */
@Component
public class MustangInvoiceMapper {

    /** Exemption reason codes (BT-121) for the categories that require one. */
    private static final Map<TaxCategory, String> EXEMPTION_CODES = Map.of(
            TaxCategory.REVERSE_CHARGE_EU, "VATEX-EU-AE",
            TaxCategory.NOT_TAXABLE_ABROAD, "VATEX-EU-O");

    private static final String SEPA_CREDIT_TRANSFER = "58";

    private final ZoneId zone;

    /** Creates a mapper that converts dates in the system time zone. */
    public MustangInvoiceMapper() {
        this(ZoneId.systemDefault());
    }

    MustangInvoiceMapper(ZoneId zone) {
        this.zone = zone;
    }

    /**
     * Maps the invoice.
     *
     * @param document the invoice
     * @return the Mustang invoice
     */
    public Invoice toMustang(InvoiceDocument document) {
        Invoice invoice = new Invoice()
                .setNumber(document.number())
                .setCurrency("EUR")
                .setIssueDate(date(document.issueDate()))
                .setDueDate(date(document.dueDate()))
                .setDeliveryDate(date(document.serviceTo()))
                .setDetailedDeliveryPeriod(date(document.serviceFrom()), date(document.serviceTo()))
                .setSender(seller(document))
                .setRecipient(party(document.buyer()))
                .setPaymentTermDescription("Zahlbar bis " + document.dueDate());
        if (document.buyerReference() != null) {
            invoice.setReferenceNumber(document.buyerReference());
        }
        String exemptReason = exemptReason(document);
        for (InvoiceLine line : document.lines()) {
            invoice.addItem(item(line, document, exemptReason));
        }
        return invoice;
    }

    /**
     * Returns the exemption reason (BT-120) shared by all lines with code E.
     *
     * <p>EN 16931 allows one VAT breakdown per code (BR-E-08), so §19 and §4 Nr. 21 lines share one
     * reason that joins both notes.
     */
    private static String exemptReason(InvoiceDocument document) {
        if (document.taxNote() != null && !document.taxNote().isBlank()) {
            return document.taxNote();
        }
        return document.categories().stream()
                .filter(category -> "E".equals(category.code()))
                .map(TaxCategory::defaultNote)
                .collect(Collectors.joining(" "));
    }

    private static Item item(InvoiceLine line, InvoiceDocument document, String exemptReason) {
        TaxCategory category = line.category();
        Product product = new Product(line.text(), "", line.unitCode(), BigDecimal.ZERO)
                .setTaxCategoryCode(category.code());
        if ("E".equals(category.code())) {
            product.setTaxExemptionReason(exemptReason);
        } else {
            product.setTaxExemptionReason(noteFor(category, document))
                    .setTaxExemptionReasonCode(EXEMPTION_CODES.get(category));
        }
        return new Item(product, line.unitPrice(), line.quantity());
    }

    private static String noteFor(TaxCategory category, InvoiceDocument document) {
        String note = document.taxNote();
        return note == null || note.isBlank() ? category.defaultNote() : note;
    }

    private static TradeParty seller(InvoiceDocument document) {
        Party seller = document.seller();
        TradeParty party = party(seller)
                .setContact(new Contact(seller.contactName(), seller.phone(), seller.email()))
                .addBankDetails(new BankDetails(document.iban(), document.bic())
                        .setPaymentMeansCode(SEPA_CREDIT_TRANSFER));
        if (seller.taxNumber() != null) {
            party.addTaxID(seller.taxNumber());
            if (seller.vatId() == null) {
                // BR-CO-26 needs BT-29, BT-30 or BT-31. Without a VAT ID the tax number serves as BT-29.
                party.setID(seller.taxNumber());
            }
        }
        return party;
    }

    private static TradeParty party(Party source) {
        TradeParty party = new TradeParty(source.name(), source.street(), source.postcode(), source.city(),
                source.countryCode());
        if (source.email() != null) {
            party.setEmail(source.email());
        }
        if (source.vatId() != null) {
            party.addVATID(source.vatId());
        }
        return party;
    }

    private Date date(LocalDate date) {
        return Date.from(date.atStartOfDay(zone).toInstant());
    }
}
