package io.github.grigoriev.im.einvoice;

/**
 * The information block of the PDF, right of the address field: 83 mm wide, its labels and values at 8.5 pt. The font
 * is monospace, so the length of a text tells its width.
 */
public final class InfoBox {

    /**
     * The characters of a value that fit beside the whole label "Geschäftspartnernummer" of 22 characters. Measured in
     * the PDF: the table layout of Flying Saucer leaves less than the arithmetic. The broken label leaves 20.
     */
    static final int BESIDE_PARTNER_LABEL = 15;

    private InfoBox() {
    }

    /**
     * Tells whether the label of the Geschäftspartnernummer breaks after "Geschäftspartner-", so a long value gets
     * more width. A soft hyphen would do it alone, but Flying Saucer prints it unbroken too.
     *
     * @param sellerIdentifier the Geschäftspartnernummer, may be {@code null}
     * @return {@code true} if the value does not fit beside the whole label
     */
    public static boolean breaksPartnerLabel(String sellerIdentifier) {
        return sellerIdentifier != null && sellerIdentifier.length() > BESIDE_PARTNER_LABEL;
    }
}
