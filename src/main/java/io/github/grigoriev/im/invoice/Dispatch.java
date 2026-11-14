package io.github.grigoriev.im.invoice;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One dispatch of an issued invoice. Written once, the database refuses a change.
 *
 * @param id database ID, {@code null} before the save
 * @param invoiceId the invoice
 * @param sentOn the day it went out
 * @param channel the way
 * @param recipient to whom, {@code null} if not given
 * @param note a note, {@code null} if not given
 * @param username who recorded it
 * @param createdAt when it was recorded
 */
@Table("dispatch")
public record Dispatch(@Id Long id, long invoiceId, LocalDate sentOn, DispatchChannel channel, String recipient,
        String note, String username, Instant createdAt) {
}
