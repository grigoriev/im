package io.github.grigoriev.im.customer;

import io.github.grigoriev.im.einvoice.InvoiceFormat;

/** Customers for tests. Names and addresses are invented. */
public final class CustomerTestData {

    private CustomerTestData() {
    }

    /**
     * Returns a valid customer with ZUGFeRD EN16931.
     *
     * @return the customer
     */
    public static Customer verlag() {
        return new Customer(null, "Verlag am Fluss GmbH", "Hafenstraße 3", "20457", "Hamburg", "DE", null,
                "buchhaltung@verlag.example", null, null, InvoiceFormat.ZUGFERD_EN16931);
    }

    /**
     * Returns a valid public customer with XRechnung.
     *
     * @return the customer
     */
    public static Customer vhs() {
        return new Customer(null, "Volkshochschule Musterstadt", "Am Markt 1", "86150", "Musterstadt", "DE",
                "Frau Beispiel", "erechnung@vhs.example", null, "09162000-VHS01-42", InvoiceFormat.XRECHNUNG);
    }
}
