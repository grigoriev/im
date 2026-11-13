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
}
