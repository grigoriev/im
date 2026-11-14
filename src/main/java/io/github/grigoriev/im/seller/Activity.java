package io.github.grigoriev.im.seller;

/**
 * The business an invoice belongs to when the Finanzamt gave teaching a tax number of its own (decision 2026-11-14).
 * It picks the tax number of the invoice and its Anlage EÜR, apart from the tax category of the lines: teaching abroad
 * is not taxable in Germany, yet it belongs to teaching.
 */
public enum Activity {

    /** Teaching, with the tax number for teaching. */
    TEACHING("Unterricht"),
    /** Every other business, with the main tax number. */
    OTHER("Übrige Leistungen");

    private final String label;

    Activity(String label) {
        this.label = label;
    }

    /**
     * Returns the German name.
     *
     * @return the label
     */
    public String label() {
        return label;
    }
}
