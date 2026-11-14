package io.github.grigoriev.im.einvoice;

/** Gives tests outside this package access to the sample invoices. */
public final class ItSamples {

    private ItSamples() {
    }

    /**
     * Returns the sample reading invoice.
     *
     * @param format the format
     * @return the invoice
     */
    public static InvoiceDocument reading(InvoiceFormat format) {
        return SampleInvoices.reading(format);
    }

    /**
     * Returns the sample reading invoice of a seller without e-mail and phone.
     *
     * @param format the format
     * @return the invoice
     */
    public static InvoiceDocument readingWithoutContact(InvoiceFormat format) {
        return SampleInvoices.invoice(SampleInvoices.SELLER_NO_CONTACT, SampleInvoices.PUBLISHER, null, format,
                SampleInvoices.lump("Lesung aus „Der stille Hafen“ am 16.03.2027, 90 Min.", "400.00",
                        TaxCategory.KLEINUNTERNEHMER));
    }

    /**
     * Returns a document whose buyer names a contact person, for the lists that show it.
     *
     * @param document the document
     * @param contact the contact person of the buyer
     * @return the copy
     */
    public static InvoiceDocument withBuyerContact(InvoiceDocument document, String contact) {
        Party b = document.buyer();
        Party buyer = new Party(b.name(), b.street(), b.postcode(), b.city(), b.countryCode(), b.email(), b.phone(),
                contact, b.taxNumber(), b.vatId());
        return new InvoiceDocument(document.number(), document.issueDate(), document.serviceFrom(),
                document.serviceTo(), document.dueDate(), document.seller(), buyer, document.buyerReference(),
                document.iban(), document.bic(), document.intro(), document.closing(), document.taxNote(),
                document.format(), document.lines(), document.cancels());
    }
}
