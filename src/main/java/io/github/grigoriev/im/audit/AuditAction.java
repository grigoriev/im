package io.github.grigoriev.im.audit;

/** What happened. The label is the German text of the log page. */
public enum AuditAction {

    /** A login with password, passkey or "Angemeldet bleiben". */
    LOGIN("Angemeldet"),
    /** A login that failed. It names the user only if that user exists, never the entered text. */
    LOGIN_FAILED("Anmeldung fehlgeschlagen"),
    /** The admin created a user. */
    USER_CREATED("Benutzer angelegt"),
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
    /** The pattern of the invoice numbers changed. */
    NUMBER_PATTERN_SAVED("Nummernmuster gespeichert"),
    /** The start value of a year changed. */
    NUMBER_START_SET("Startnummer gesetzt"),
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
    TEMPLATE_DELETED("Vorlage gelöscht");

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
