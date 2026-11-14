package io.github.grigoriev.im.audit;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One entry of the audit log.
 *
 * @param id database ID, {@code null} before the first save
 * @param at time of the event
 * @param username who did it, {@code system} for the app itself
 * @param action what happened
 * @param detail what it concerned, for example a customer name; never a password or a token
 * @param invoiceId the invoice it concerned, {@code null} for none; it puts the entry into the history of the invoice
 */
@Table("audit_event")
public record AuditEvent(@Id Long id, Instant at, String username, AuditAction action, String detail,
        Long invoiceId) {
}
