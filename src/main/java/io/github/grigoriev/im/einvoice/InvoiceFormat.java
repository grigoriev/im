package io.github.grigoriev.im.einvoice;

/** The e-invoice formats the app issues, see section 4 of {@code docs/PLAN.md}. */
public enum InvoiceFormat {

    /** ZUGFeRD with profile EN16931: PDF/A-3 with embedded CII XML. */
    ZUGFERD_EN16931(true, false),

    /** ZUGFeRD with profile XRECHNUNG: PDF/A-3 with embedded CII XML that follows XRechnung. */
    ZUGFERD_XRECHNUNG(true, true),

    /** XRechnung as CII XML only. */
    XRECHNUNG(false, true);

    private final boolean pdf;
    private final boolean xrechnungRules;

    InvoiceFormat(boolean pdf, boolean xrechnungRules) {
        this.pdf = pdf;
        this.xrechnungRules = xrechnungRules;
    }

    /**
     * Tells whether the e-invoice is a PDF with embedded XML.
     *
     * @return {@code true} for both ZUGFeRD formats
     */
    public boolean isPdf() {
        return pdf;
    }

    /**
     * Tells whether the XML must follow the XRechnung rules, for example BR-DE-15.
     *
     * @return {@code true} for both XRechnung formats
     */
    public boolean followsXRechnung() {
        return xrechnungRules;
    }
}
