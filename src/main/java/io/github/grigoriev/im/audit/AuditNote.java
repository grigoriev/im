package io.github.grigoriev.im.audit;

/**
 * The details of a login that every audit entry names, for example the API token a change came through.
 */
public interface AuditNote {

    /**
     * Returns the note for the audit entry.
     *
     * @return for example "API-Token „Claude Code“"
     */
    String auditNote();
}
