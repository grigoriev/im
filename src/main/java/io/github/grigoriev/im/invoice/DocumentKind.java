package io.github.grigoriev.im.invoice;

/** The documents of an issued invoice. */
public enum DocumentKind {

    /** The PDF: the e-invoice for ZUGFeRD, a viewing copy for XRechnung. */
    PDF("application/pdf", ".pdf"),
    /** The XML: the e-invoice for XRechnung, a copy of the embedded XML for ZUGFeRD. */
    XML("application/xml", ".xml");

    private final String mediaType;
    private final String extension;

    DocumentKind(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    /**
     * Returns the media type.
     *
     * @return for example {@code application/pdf}
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * Returns the file name of an invoice's document.
     *
     * @param number the invoice number
     * @return for example {@code Rechnung_2027-01.pdf}; a slash of the number becomes a hyphen
     */
    public String filename(String number) {
        return "Rechnung_" + number.replace('/', '-') + extension;
    }
}
