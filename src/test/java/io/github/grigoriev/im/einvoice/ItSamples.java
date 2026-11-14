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
}
