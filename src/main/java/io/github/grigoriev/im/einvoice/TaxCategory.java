package io.github.grigoriev.im.einvoice;

/**
 * VAT treatment of an invoice line, see section 6 of {@code docs/PLAN.md}.
 *
 * <p>The tax notes are defaults. A template or an invoice can replace them.
 */
public enum TaxCategory {

    /** Kleinunternehmer, §19 UStG. Counts toward the §19 threshold. */
    KLEINUNTERNEHMER("E", "Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.", true),

    /** Teaching, VAT-exempt under §4 Nr. 21 UStG. */
    EXEMPT_4_21("E", "Steuerfreie Leistung gemäß § 4 Nr. 21 UStG.", false),

    /** B2B service to a business in another EU country. */
    REVERSE_CHARGE_EU("AE", "Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge).", false),

    /** Service to a customer outside the EU, not taxable in Germany. */
    NOT_TAXABLE_ABROAD("O", "Nicht im Inland steuerbare Leistung.", false);

    private final String code;
    private final String defaultNote;
    private final boolean countsForThreshold;

    TaxCategory(String code, String defaultNote, boolean countsForThreshold) {
        this.code = code;
        this.defaultNote = defaultNote;
        this.countsForThreshold = countsForThreshold;
    }

    /**
     * Returns the EN 16931 VAT category code (BT-151).
     *
     * @return the code, for example {@code E}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the default tax note printed on the invoice.
     *
     * @return the tax note
     */
    public String defaultNote() {
        return defaultNote;
    }

    /**
     * Tells whether revenue in this category counts toward the §19 threshold.
     *
     * @return {@code true} for the Kleinunternehmer category only
     */
    public boolean countsForThreshold() {
        return countsForThreshold;
    }
}
