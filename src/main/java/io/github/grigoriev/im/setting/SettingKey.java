package io.github.grigoriev.im.setting;

/** The keys of table {@code app_setting}, each with the text it is stored under. */
public enum SettingKey {

    /** The pattern of the invoice numbers. */
    INVOICE_NUMBER_PATTERN("invoice-number-pattern"),

    /** The default subject of an invoice e-mail. */
    MAIL_SUBJECT("mail-subject"),

    /** The default text of an invoice e-mail. */
    MAIL_BODY("mail-body"),

    /** The default subject of a payment reminder. */
    REMINDER_SUBJECT("reminder-subject"),

    /** The default text of a payment reminder. */
    REMINDER_BODY("reminder-body"),

    /** The first day of the Verfahrensdokumentation. */
    PROCEDURE_VALID_FROM("procedure-valid-from"),

    /** Where the Verfahrensdokumentation says the expenses are kept. */
    PROCEDURE_EXPENSES("procedure-expenses"),

    /** Where the Verfahrensdokumentation says the yearly archives are kept. */
    PROCEDURE_ARCHIVE("procedure-archive");

    private final String stored;

    SettingKey(String stored) {
        this.stored = stored;
    }

    /** @return the key in the database */
    public String stored() {
        return stored;
    }
}
