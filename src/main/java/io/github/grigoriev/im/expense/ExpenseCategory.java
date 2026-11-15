package io.github.grigoriev.im.expense;

/** The kind of an expense, in the order and with the names of the expenses of the Anlage EÜR and of WISO Steuer. */
public enum ExpenseCategory {
    /** Goods and material that go into the work. */
    MATERIAL("Waren und Material"),
    /** Work of others bought in, for example a translation. */
    SERVICES("Fremdleistungen"),
    /** Rent and costs of a room used for the business only. */
    ROOM("Raumkosten"),
    /** Phone and internet. */
    TELECOM("Telefon und Internet"),
    /** Software, cloud services, hosting. */
    SOFTWARE("Software und EDV"),
    /** Tools of the work up to 800 € net, or an asset marked as such. */
    WORK_EQUIPMENT("Arbeitsmittel"),
    /** Office supplies and postage. */
    OFFICE("Bürobedarf und Porto"),
    /** Books and journals of the profession. */
    LITERATURE("Fachliteratur"),
    /** Courses, seminars, conferences. */
    TRAINING("Fortbildung"),
    /** Trips: tickets, hotels. */
    TRAVEL("Reisekosten"),
    /** Public transport and taxis in town. */
    TRANSPORT("Fahrtkosten"),
    /** Advertising and a website. */
    ADVERTISING("Werbung"),
    /** Fees, memberships and insurances of the business. */
    INSURANCE("Beiträge und Versicherungen"),
    /** Tax advice and legal advice. */
    ADVICE("Rechts- und Steuerberatung"),
    /** Fees of the business account. */
    BANK("Kontoführung"),
    /** Gifts to customers. */
    GIFTS("Geschenke"),
    /** Everything else. */
    OTHER("Sonstiges");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    /**
     * Returns the German name.
     *
     * @return the name the pages and the CSV show
     */
    public String label() {
        return label;
    }
}
