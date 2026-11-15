package io.github.grigoriev.im.document;

/** The kind of a business document. The label is the German text of the pages. */
public enum DocumentCategory {

    /** The Verfahrensdokumentation of the invoicing (GoBD). */
    PROCEDURE("Verfahrensdokumentation"),
    /** A tax assessment, for example the Einkommensteuerbescheid. */
    TAX_ASSESSMENT("Steuerbescheid"),
    /** Another letter of the tax office, for example the one with a tax number. */
    TAX_OFFICE("Finanzamt"),
    /** A certificate, for example the one for teaching under §4 Nr. 21 UStG. */
    CERTIFICATE("Bescheinigung"),
    /** A contract with a customer. */
    CONTRACT("Vertrag"),
    /** Anything else worth keeping. */
    OTHER("Sonstiges");

    private final String label;

    DocumentCategory(String label) {
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
