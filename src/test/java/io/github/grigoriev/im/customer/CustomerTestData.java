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
        return new Customer(null, "Verlag am Fluss GmbH", null, "Hafenstraße 3", "20457", "Hamburg", "DE", null,
                "buchhaltung@verlag.example", null, null, null, InvoiceFormat.ZUGFERD_EN16931, null);
    }

    /**
     * Returns a valid public customer with XRechnung.
     *
     * @return the customer
     */
    public static Customer vhs() {
        return new Customer(null, "Volkshochschule Musterstadt", null, "Am Markt 1", "86150", "Musterstadt", "DE",
                "Frau Beispiel", "erechnung@vhs.example", null, "09162000-VHS01-42", null, InvoiceFormat.XRECHNUNG, null);
    }

    /**
     * Returns a valid customer with a department, a contact person and a Geschäftspartnernummer.
     *
     * @return the customer
     */
    public static Customer school() {
        return new Customer(null, "Sprachschule am See", "Abrechnungen Online-Sprachkurse", "Seeweg 5", "86150",
                "Musterstadt", "DE", "Frau Weber", "weber@sprachschule.example", null, null,
                "70001234, Sprachzentrum Musterstadt / 1024", InvoiceFormat.ZUGFERD_EN16931, null);
    }
}
