package io.github.grigoriev.im.seller;

/** How the seller charges VAT (docs/PLAN.md, section 6). */
public enum TaxStatus {

    /** Kleinunternehmer under §19 UStG: no VAT, the revenue counts toward the threshold. */
    KLEINUNTERNEHMER("Kleinunternehmer nach § 19 UStG"),

    /** Regular VAT at 19 % and 7 %. */
    REGULAR("Regelbesteuerung (19 % und 7 %)");

    private final String label;

    TaxStatus(String label) {
        this.label = label;
    }

    /**
     * Returns the German label of the UI.
     *
     * @return the label
     */
    public String label() {
        return label;
    }
}
