package io.github.grigoriev.im.audit;

/** What happened. The label is the German text of the log page. */
public enum AuditAction {

    /** A login with password, passkey or "Angemeldet bleiben". */
    LOGIN("Angemeldet"),
    /** A login that failed. It names the user only if that user exists, never the entered text. */
    LOGIN_FAILED("Anmeldung fehlgeschlagen"),
    /** The admin created a user. */
    USER_CREATED("Benutzer angelegt"),
    /** The admin deleted a user. */
    USER_DELETED("Benutzer gelöscht"),
    /** The admin set a new password for a user. */
    PASSWORD_RESET("Passwort zurückgesetzt"),
    /** A user changed the own password. */
    PASSWORD_CHANGED("Passwort geändert"),
    /** A user added a passkey. */
    PASSKEY_ADDED("Passkey hinzugefügt"),
    /** A user renamed a passkey. */
    PASSKEY_RENAMED("Passkey umbenannt"),
    /** A user removed a passkey. */
    PASSKEY_REMOVED("Passkey entfernt"),
    /** The admin removed every passkey of a user. */
    PASSKEYS_REMOVED("Alle Passkeys entfernt"),
    /** The seller profile changed. */
    PROFILE_SAVED("Profil gespeichert"),
    /** The default tax notes changed. */
    TAX_NOTES_SAVED("Steuerhinweise gespeichert"),
    /** The default e-mail text changed. */
    MAIL_TEXT_SAVED("E-Mail-Text gespeichert"),
    /** The §19 revenue of a year before the app changed. */
    BASELINE_SAVED("Vorjahresumsatz gespeichert"),
    /** The admin downloaded the full export. */
    DATA_EXPORTED("Datensicherung exportiert"),
    /** The admin read a full export into an empty installation. */
    DATA_IMPORTED("Datensicherung eingespielt"),
    /** The pattern of the invoice numbers changed. */
    NUMBER_PATTERN_SAVED("Nummernmuster gespeichert"),
    /** The start value of a year changed. */
    NUMBER_START_SET("Startnummer gesetzt"),
    /** The numbers of a year were switched to by hand, or back to the counter. */
    NUMBERS_BY_HAND("Nummern von Hand umgestellt"),
    /** The reason of a gap in the numbers of a year by hand was written. */
    NUMBER_GAP_EXPLAINED("Lücke begründet"),
    /** A year by hand was closed: the counter continues after its highest number. */
    NUMBER_YEAR_CLOSED("Jahr abgeschlossen"),
    /** A country joined the list of the forms. */
    COUNTRY_ADDED("Land hinzugefügt"),
    /** A country left the list of the forms. */
    COUNTRY_REMOVED("Land entfernt"),
    /** A customer was created. */
    CUSTOMER_CREATED("Kunde angelegt"),
    /** A customer changed. */
    CUSTOMER_CHANGED("Kunde geändert"),
    /** A customer was deleted. */
    CUSTOMER_DELETED("Kunde gelöscht"),
    /** A draft invoice was created. */
    DRAFT_CREATED("Entwurf angelegt"),
    /** A draft invoice was deleted. */
    DRAFT_DELETED("Entwurf gelöscht"),
    /** A template was created. */
    TEMPLATE_CREATED("Vorlage angelegt"),
    /** A template was deleted. */
    TEMPLATE_DELETED("Vorlage gelöscht"),
    /** An invoice was issued with its number and documents. */
    INVOICE_ISSUED("Rechnung ausgestellt"),
    /** An external invoice was registered with its number and original file. */
    INVOICE_REGISTERED("Externe Rechnung erfasst"),
    /** A further original file of an external invoice was added. */
    ORIGINAL_ADDED("Datei hinzugefügt"),
    /** An invoice was cancelled by the issue of its Stornorechnung. */
    INVOICE_CANCELLED("Rechnung storniert"),
    /** A dispatch of an invoice was recorded. */
    INVOICE_SENT("Versand vermerkt"),
    /** A payment or a refund of an invoice was recorded. */
    PAYMENT_RECORDED("Zahlung erfasst"),
    /** A payment reminder of an invoice was sent or noted. */
    REMINDER_RECORDED("Zahlungserinnerung vermerkt"),
    /** A business document was stored. */
    DOCUMENT_ADDED("Dokument abgelegt"),
    /** A new version of a business document was stored. */
    DOCUMENT_REPLACED("Neue Fassung abgelegt"),
    /** The description of a business document changed. */
    DOCUMENT_CHANGED("Dokument geändert"),
    /** The check found a document or snapshot changed outside the app, or broken. */
    INTEGRITY_FAILED("Prüfung fehlgeschlagen"),
    /** An expense was entered with its receipt. */
    EXPENSE_ADDED("Ausgabe erfasst"),
    /** The details of an expense changed. */
    EXPENSE_CHANGED("Ausgabe geändert"),
    /** An expense was cancelled. */
    EXPENSE_CANCELLED("Ausgabe storniert");

    private final String label;

    AuditAction(String label) {
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
