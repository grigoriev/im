package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;

/**
 * VAT treatment of an invoice line, see section 6 of {@code docs/PLAN.md}.
 *
 * <p>The tax notes are defaults. A template or an invoice can replace them. The standard rates serve
 * every business that charges VAT, for example a shop that sells goods.
 */
public enum TaxCategory {

    /** Kleinunternehmer, §19 UStG. Counts toward the §19 threshold. */
    KLEINUNTERNEHMER("E", "§ 19", "Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.", true, BigDecimal.ZERO),

    /** Teaching, VAT-exempt under §4 Nr. 21 UStG. */
    EXEMPT_4_21("E", "§ 4 Nr. 21", "Steuerfrei gemäß § 4 Nr. 21 UStG.", false, BigDecimal.ZERO),

    /** B2B service to a business in another EU country. */
    REVERSE_CHARGE_EU("AE", "Reverse Charge", "Steuerschuldnerschaft des Leistungsempfängers.", false, BigDecimal.ZERO),

    /** Service to a customer outside the EU, not taxable in Germany. */
    NOT_TAXABLE_ABROAD("O", "nicht steuerbar", "Nicht im Inland steuerbare Leistung.", false, BigDecimal.ZERO),

    /** Standard rate, 19 % (§12 Abs. 1 UStG). */
    STANDARD_19("S", "19\u00a0%", "", false, new BigDecimal("19")),

    /** Reduced rate, 7 % (§12 Abs. 2 UStG), for example books. */
    STANDARD_7("S", "7\u00a0%", "", false, new BigDecimal("7"));

    private final String code;
    private final String label;
    private final String defaultNote;
    private final boolean countsForThreshold;
    private final BigDecimal rate;

    TaxCategory(String code, String label, String defaultNote, boolean countsForThreshold, BigDecimal rate) {
        this.code = code;
        this.label = label;
        this.defaultNote = defaultNote;
        this.countsForThreshold = countsForThreshold;
        this.rate = rate;
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
     * Returns the short label of the tax column on the PDF, for example {@code 19 %} or {@code § 4 Nr. 21}.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the default tax note printed on the invoice.
     *
     * @return the tax note, empty for the standard rates
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

    /**
     * Returns the VAT rate in percent (BT-152).
     *
     * @return 19 or 7 for the standard rates, 0 otherwise
     */
    public BigDecimal rate() {
        return rate;
    }

    /**
     * Tells whether the seller charges VAT on the line.
     *
     * @return {@code true} for the standard rates
     */
    public boolean chargesVat() {
        return rate.signum() > 0;
    }
}
