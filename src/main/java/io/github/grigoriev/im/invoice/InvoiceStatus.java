package io.github.grigoriev.im.invoice;

/** The life of an invoice (docs/PLAN.md, section 6). Only a draft can change. */
public enum InvoiceStatus {

    /** Can change or be deleted. Has no number. */
    DRAFT("Entwurf"),
    /** Has its number, its snapshots and its frozen documents. */
    ISSUED("Ausgestellt"),
    /** Sent at least once. */
    SENT("Versendet"),
    /** The payments cover the total. */
    PAID("Bezahlt"),
    /** Cancelled by a Stornorechnung. */
    CANCELLED("Storniert");

    private final String label;

    InvoiceStatus(String label) {
        this.label = label;
    }

    /**
     * Returns the German label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }
}
