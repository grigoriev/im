package io.github.grigoriev.im.api;

import io.github.grigoriev.im.audit.AuditNote;

/**
 * The token behind a request of the API, kept as the details of its login.
 *
 * @param tokenId the ID of the token
 * @param name the name of the token
 */
public record ApiTokenDetails(long tokenId, String name) implements AuditNote {

    @Override
    public String auditNote() {
        return "API-Token „" + name + "“";
    }
}
